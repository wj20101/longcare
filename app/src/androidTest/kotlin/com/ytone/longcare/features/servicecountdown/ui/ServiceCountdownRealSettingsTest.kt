package com.ytone.longcare.features.servicecountdown.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.UiAutomation
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.R
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.theme.LongCareTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Emulator-only checks against real Settings, platform capabilities and ActivityResultRegistry.
 * No care-order APIs or location service starts are called; the granted callback only counts locally.
 *
 * Opt in with `-e runRealSettings true` and run the two methods separately, with actual precise-location/notification grants, location enabled,
 * and an unrestricted reminder channel. Before each invocation the external device runner must save
 * both package/UID app-op modes and prepare the relevant state:
 * - exact_alarm_settings_cancel_then_grant_reads_real_capability: exact alarm denied.
 * - full_screen_settings_cancel_then_grant_reads_real_capability: exact alarm allowed, FSI denied.
 *
 * The exact-alarm test intentionally leaves its real UI grant in place. Restoring denial can kill the
 * target/instrumentation process, so the external runner MUST restore its saved modes after the test
 * process exits, including failure paths. FSI restores its original UID mode in finally; the external
 * runner must still restore the complete original package/UID snapshot. No app data is cleared.
 */
@SdkSuppress(minSdkVersion = 34)
class ServiceCountdownRealSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val automation: UiAutomation get() = instrumentation.uiAutomation
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)
    private val notifications: NotificationManager get() = context.getSystemService(NotificationManager::class.java)

    @Test fun exact_alarm_settings_cancel_then_grant_reads_real_capability() {
        requireEmulatorAndIssue(ServiceCountdownPermissionIssue.EXACT_ALARM)
        assertFalse(alarms.canScheduleExactAlarms())
        val fixture = Fixture(ServiceCountdownPermissionIssue.EXACT_ALARM)
        render(fixture)

        openRealSettings()
        returnFromSettings(fixture)
        assertFalse(alarms.canScheduleExactAlarms())
        assertNoRepeatedExplanation(fixture)

        // Reopening is an explicit user action after cancelling the previous request.
        compose.onNodeWithText(RECHECK).performClick()
        openRealSettings()
        enableSingleSettingsSwitch { alarms.canScheduleExactAlarms() }
        returnFromSettings(fixture)
        compose.runOnIdle {
            assertTrue(alarms.canScheduleExactAlarms())
            assertEquals(currentIssue(), fixture.lastIssue)
        }
    }

    @Test fun full_screen_settings_cancel_then_grant_reads_real_capability() {
        requireEmulatorAndIssue(ServiceCountdownPermissionIssue.FULL_SCREEN)
        assertFalse(notifications.canUseFullScreenIntent())
        val originalMode = readFullScreenUidMode()
        try {
            val fixture = Fixture(ServiceCountdownPermissionIssue.FULL_SCREEN)
            render(fixture)

            openRealSettings()
            returnFromSettings(fixture)
            assertFalse(notifications.canUseFullScreenIntent())
            assertNoRepeatedExplanation(fixture)

            compose.onNodeWithText(RECHECK).performClick()
            openRealSettings()
            enableSingleSettingsSwitch { notifications.canUseFullScreenIntent() }
            returnFromSettings(fixture)
            compose.runOnIdle {
                assertTrue(notifications.canUseFullScreenIntent())
                assertEquals(null, fixture.lastIssue)
            }
        } finally {
            // FSI uses a UID app-op in AOSP. Never revoke exact alarms from this running process.
            shell("appops set --uid ${context.packageName} USE_FULL_SCREEN_INTENT $originalMode")
            waitFor("original full-screen capability was not restored") {
                !notifications.canUseFullScreenIntent()
            }
        }
    }

    private fun render(fixture: Fixture) {
        compose.setContent {
            LongCareTheme {
                var issue by remember { mutableStateOf<ServiceCountdownPermissionIssue?>(fixture.initialIssue) }
                val launchers = rememberServiceCountdownScreenLaunchers(
                    orderKey = OrderKey(-9_876_543, -1),
                    canScheduleExactAlarms = alarms::canScheduleExactAlarms,
                    canUseFullScreenIntent = notifications::canUseFullScreenIntent,
                    onLocationPermissionGranted = { fixture.locationCallbacks.incrementAndGet() },
                    onPermissionDialogRequired = {
                        fixture.lastIssue = it
                        fixture.resultCallbacks.incrementAndGet()
                        issue = it
                    },
                )
                TextButton(onClick = launchers.checkPermissions) { Text(RECHECK) }
                val displayed = issue
                if (displayed != null) {
                    PermissionAlertDialog(
                        visible = true,
                        message = stringResource(displayed.messageRes),
                        onDismiss = { issue = null },
                        confirmLabelRes = R.string.common_go_to_settings,
                        onConfirm = { launchers.requestPermission(displayed) },
                    )
                }
            }
        }
    }

    private fun openRealSettings() {
        compose.onNodeWithText(context.getString(R.string.common_go_to_settings)).performClick()
        waitFor("real Settings did not become foreground") {
            automation.rootInActiveWindow?.packageName?.toString() == SETTINGS_PACKAGE
        }
    }

    private fun returnFromSettings(fixture: Fixture) {
        val callbacksBeforeBack = fixture.resultCallbacks.get()
        // A real framework Back, not registry.dispatchResult or a direct callback invocation.
        shell("input keyevent KEYCODE_BACK")
        compose.waitUntil(10_000) { fixture.resultCallbacks.get() > callbacksBeforeBack }
        compose.runOnIdle { assertTrue(fixture.locationCallbacks.get() > 0) }
    }

    private fun assertNoRepeatedExplanation(fixture: Fixture) {
        compose.runOnIdle { assertEquals(null, fixture.lastIssue) }
        compose.onNode(isDialog()).assertDoesNotExist()
    }

    private fun enableSingleSettingsSwitch(capabilityGranted: () -> Boolean) {
        var readySwitch: AccessibilityNodeInfo? = null
        waitFor("Expected one enabled permission switch on the real special-access page") {
            // Settings can expose its window before the asynchronously loaded preference is ready.
            val root = automation.rootInActiveWindow
            readySwitch = if (root?.packageName?.toString() == SETTINGS_PACKAGE) {
                descendants(root).filter { it.isCheckable && it.isEnabled }.singleOrNull()
            } else null
            readySwitch != null
        }
        val toggle = requireNotNull(readySwitch)
        assertFalse("Permission switch should initially be off", toggle.isChecked)
        val clickTarget = generateSequence(toggle) { it.parent }.firstOrNull { it.isClickable }
        assertTrue("Permission switch has no clickable control",
            clickTarget?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        waitFor("real platform capability did not become granted", capabilityGranted)
    }

    private fun descendants(node: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        yield(node)
        for (index in 0 until node.childCount) {
            node.getChild(index)?.let { yieldAll(descendants(it)) }
        }
    }

    private fun currentIssue(): ServiceCountdownPermissionIssue? = requiredCountdownPermissionIssue(
        context, alarms.canScheduleExactAlarms(), notifications.canUseFullScreenIntent(),
    )

    private fun requireEmulatorAndIssue(issue: ServiceCountdownPermissionIssue) {
        assumeTrue("A runner that restores app-ops must explicitly enable this test",
            InstrumentationRegistry.getArguments().getString("runRealSettings") == "true")
        assumeTrue("This test may change Settings only on an emulator",
            Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish")
        assertEquals("Prepare real location/notification and special-access preconditions externally",
            issue, currentIssue())
    }

    private fun readFullScreenUidMode(): String {
        val output = shell("appops get --uid ${context.packageName} USE_FULL_SCREEN_INTENT")
        if (output.contains("No operations.")) return "default"
        return requireNotNull(Regex("USE_FULL_SCREEN_INTENT:\\s*(allow|ignore|deny|default|foreground|errored)")
            .find(output)?.groupValues?.get(1)) { "Cannot safely snapshot FSI UID mode: $output" }
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        automation.executeShellCommand(command),
    ).bufferedReader().use { it.readText() }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (!condition() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue(message, condition())
    }

    private class Fixture(val initialIssue: ServiceCountdownPermissionIssue) {
        val resultCallbacks = AtomicInteger()
        val locationCallbacks = AtomicInteger()
        @Volatile var lastIssue: ServiceCountdownPermissionIssue? = initialIssue
    }

    private companion object {
        const val SETTINGS_PACKAGE = "com.android.settings"
        const val RECHECK = "重新检查权限"
    }
}
