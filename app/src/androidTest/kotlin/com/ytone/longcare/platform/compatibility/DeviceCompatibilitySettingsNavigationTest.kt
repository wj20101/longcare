package com.ytone.longcare.platform.compatibility

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.LocalActivity
import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.PermissionGuideId
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.core.ui.permission.BackgroundSettingsDialog
import com.ytone.longcare.theme.LongCareTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opens real Settings from the real dialog, without changing any switches or order data. */
class DeviceCompatibilitySettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun battery_settings_return_to_the_same_dialog() = verifySettingsRoundTrip(PermissionGuideId.BATTERY_OPTIMIZATION)

    @Test fun manufacturer_auto_start_settings_return_to_the_same_dialog() = verifySettingsRoundTrip(PermissionGuideId.AUTO_START)

    @Test fun manufacturer_popup_settings_return_to_the_same_dialog() = verifySettingsRoundTrip(PermissionGuideId.MANUFACTURER_POPUP)

    private fun verifySettingsRoundTrip(id: PermissionGuideId) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val guides = DeviceCompatibilityHelper.getAllGuides(context)
        val item = guides.firstOrNull { it.id == id }
        assumeTrue("This manufacturer has no $id guide", item != null)
        val selected = requireNotNull(item)
        val requiredBefore = DeviceCompatibilityHelper.getAllRequiredGuides(context).map { it.id }
        val refreshes = AtomicInteger()
        lateinit var activity: ComponentActivity
        var opened = false
        compose.setContent {
            activity = checkNotNull(LocalActivity.current as? ComponentActivity)
            LongCareTheme {
                BackgroundSettingsDialog(
                    initialItems = guides,
                    refreshItems = {
                        refreshes.incrementAndGet()
                        DeviceCompatibilityHelper.getAllGuides(activity)
                    },
                    onItemAction = { item ->
                        DeviceCompatibilityHelper.openGuide(activity, item).also { opened = it }
                    },
                    onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        val initialRefreshes = refreshes.get()
        val description = context.getString(R.string.background_settings_open_description, context.getString(selected.titleRes))
        val row = compose.onNodeWithContentDescription(description).fetchSemanticsNode()
        val click = requireNotNull(row.config[SemanticsActions.OnClick].action)
        // Invoke the real row action; global touch injection is restricted on some MIUI builds.
        instrumentation.runOnMainSync { assertTrue(click()) }
        assertTrue("Settings launch rejected for $id", opened)

        val automation = instrumentation.uiAutomation
        val expectedPackage = selected.settingsIntent.resolveActivity(context.packageManager)?.packageName
        val appLabel = context.applicationInfo.loadLabel(context.packageManager).toString()
        val requiresAppLabel = id == PermissionGuideId.MANUFACTURER_POPUP ||
            (selected.settingsIntent.component != null && id != PermissionGuideId.AUTO_START)
        val deadline = SystemClock.uptimeMillis() + 10_000
        var settingsRoot: AccessibilityNodeInfo? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null && root.packageName != null &&
                root.packageName.toString() in setOf(expectedPackage, "com.android.settings") &&
                (!requiresAppLabel || containsLabel(root, appLabel))) {
                settingsRoot = root
                break
            }
            SystemClock.sleep(100)
        }
        assertTrue("No real Settings window for $id (expected $expectedPackage)", settingsRoot != null)
        val hold = InstrumentationRegistry.getArguments().getString("settingsInspectionMs")?.toLongOrNull() ?: 0L
        android.util.Log.i("SettingsRoundTrip", "OPENED $id package=${settingsRoot?.packageName} appLabelPresent=${settingsRoot?.let { containsLabel(it, appLabel) }}")
        if (hold > 0) SystemClock.sleep(hold.coerceAtMost(20_000))
        assertTrue("System Back was rejected", automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
        compose.waitUntil(timeoutMillis = 10_000) { refreshes.get() > initialRefreshes }
        compose.onNodeWithText(context.getString(R.string.background_settings_title)).assertIsDisplayed()
        instrumentation.runOnMainSync { assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState) }
        assertEquals("Opening Settings must not mark a recommendation as handled", requiredBefore,
            DeviceCompatibilityHelper.getAllRequiredGuides(context).map { it.id })
        assertEquals("Returning without edits must not claim a new grant", guides.map { it.statusRes },
            DeviceCompatibilityHelper.getAllGuides(context).map { it.statusRes })
    }

    private fun containsLabel(node: AccessibilityNodeInfo, label: String): Boolean {
        if (node.text?.contains(label) == true || node.contentDescription?.contains(label) == true) return true
        return (0 until node.childCount).any { index -> node.getChild(index)?.let { containsLabel(it, label) } == true }
    }
}
