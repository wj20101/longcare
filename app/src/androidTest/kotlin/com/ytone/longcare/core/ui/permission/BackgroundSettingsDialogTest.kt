package com.ytone.longcare.core.ui.permission

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.view
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.common.utils.PermissionGuideId
import com.ytone.longcare.common.utils.PermissionGuideItem
import com.ytone.longcare.core.ui.R
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import com.ytone.longcare.core.common.R as CommonR

/** Uses local callbacks only; no real order, permission changes or manufacturer Settings are opened. */
class BackgroundSettingsDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun manual_entry_can_reopen_after_close_without_running_settings_action() {
        val fixture = Fixture()
        render(fixture)
        repeat(2) {
            compose.onNodeWithText("后台设置").performClick()
            compose.onNodeWithText("后台运行设置").assertIsDisplayed()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        }
        compose.runOnIdle {
            assertEquals(2, fixture.dismisses)
            assertEquals(0, fixture.settingsActions)
        }
    }

    @Test fun settings_action_keeps_dialog_and_does_not_claim_a_grant() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        settingsRow(fixture.items.single()).performClick()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_not_exempt)).assertIsDisplayed()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_exempt)).assertDoesNotExist()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        compose.onNodeWithText("检查系统权限").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.settingsActions)
            assertEquals(0, fixture.dismisses)
        }
    }

    @Test fun failed_settings_action_preserves_the_dialog_and_allows_an_explicit_retry() {
        val fixture = Fixture().apply { settingsOpenSuccess = false; showConfirm = true }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        settingsRow(fixture.items.single()).performClick()
        compose.onNodeWithText("后台运行设置").assertIsDisplayed()
        compose.onNodeWithText("开始服务").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(1, fixture.settingsActions)
            assertEquals(0, fixture.dismisses)
            assertEquals(0, fixture.confirms)
            fixture.settingsOpenSuccess = true
        }
        settingsRow(fixture.items.single()).performClick()
        compose.onNodeWithText("后台运行设置").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(2, fixture.settingsActions)
            assertEquals(0, fixture.dismisses)
            assertEquals(0, fixture.confirms)
        }
    }

    @Test fun explicit_system_check_invokes_only_its_callback_and_closes_the_manual_dialog() {
        val fixture = Fixture().apply { showSystemCheck = true }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        compose.onNodeWithText("检查系统权限").assertIsDisplayed().performClick()
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.systemChecks)
            assertEquals(0, fixture.settingsActions)
            assertEquals(0, fixture.dismisses)
        }
    }

    @Test fun returning_from_settings_refreshes_current_system_status() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_not_exempt)).assertIsDisplayed()
        settingsRow(fixture.items.single()).performScrollTo().performClick()
        compose.runOnIdle {
            fixture.owner.registry.currentState = Lifecycle.State.STARTED
            fixture.items = listOf(guide(CommonR.string.compatibility_status_exempt))
            fixture.owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_exempt)).assertIsDisplayed()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_not_exempt)).assertDoesNotExist()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_battery_optimization_message)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, fixture.settingsActions) }
    }

    @Test fun default_view_separates_system_battery_status_from_manufacturer_background_checks() {
        val fixture = Fixture().apply { items = fourGuides() }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fixture.items.forEach { item ->
            settingsRow(item).performScrollTo().assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
            compose.onNodeWithText(context.getString(item.titleRes)).assertIsDisplayed()
        }
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_not_exempt)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(resourceText(CommonR.string.compatibility_status_background_unknown)).performScrollTo().assertIsDisplayed()
        // 机型说明位于滚动区域底部，小视口允许滚动查看。
        compose.onNodeWithText("查看机型设置说明").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(CommonR.string.compatibility_battery_xiaomi)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(0, fixture.settingsActions)
            assertEquals(0, fixture.confirms)
        }
    }

    @Test fun manufacturer_details_can_expand_and_collapse_without_any_permission_action() {
        val fixture = Fixture()
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        val status = resourceText(CommonR.string.compatibility_status_not_exempt)
        val instructions = resourceText(CommonR.string.compatibility_battery_optimization_message)
        compose.onNodeWithText(status).assertIsDisplayed()
        compose.onNodeWithText(instructions).assertDoesNotExist()
        compose.onNodeWithText("查看机型设置说明").performScrollTo().performClick()
        compose.onAllNodesWithText(status).assertCountEquals(1)
        compose.onNodeWithText(instructions).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("收起说明").performScrollTo().performClick()
        compose.onNodeWithText(status).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(instructions).assertDoesNotExist()
        compose.onNodeWithText("查看机型设置说明").performScrollTo().assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, fixture.settingsActions)
            assertEquals(0, fixture.dismisses)
            assertEquals(0, fixture.confirms)
        }
    }

    @Test fun explicit_confirmation_is_separate_from_cancelling_the_dialog() {
        val fixture = Fixture().apply { showConfirm = true }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(1, fixture.dismisses)
            assertEquals(0, fixture.confirms)
        }
        compose.onNodeWithText("后台设置").performClick()
        compose.onNodeWithText("开始服务").performClick()
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.dismisses)
            assertEquals(1, fixture.confirms)
        }
    }

    @Test fun back_dismisses_without_confirming() {
        val fixture = Fixture().apply { showConfirm = true }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        val dialog = currentDialog()
        compose.runOnIdle { dialog.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.dismisses)
            assertEquals(0, fixture.confirms)
        }
    }

    @Test fun touching_the_backdrop_dismisses_without_confirming() {
        val fixture = Fixture().apply { showConfirm = true }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        val dialog = currentDialog()
        compose.runOnIdle {
            val downTime = SystemClock.uptimeMillis()
            // Exercise the real window's outside DOWN/UP handling without privileged system injection.
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, -1f, -1f, 0)
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                try {
                    assertTrue(dialog.onTouchEvent(event))
                } finally {
                    event.recycle()
                }
            }
        }
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, fixture.dismisses)
            assertEquals(0, fixture.confirms)
        }
    }

    @Test fun two_times_font_keeps_actions_visible_when_scrolling_to_settings() {
        val fixture = Fixture().apply {
            fontScale = 2f
            showConfirm = true
            showSystemCheck = true
            items = fourGuides()
        }
        render(fixture)
        compose.onNodeWithText("后台设置").performClick()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("开始服务").assertIsDisplayed()
        compose.onNodeWithText("检查系统权限").assertIsDisplayed()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val popupTitle = context.getString(CommonR.string.compatibility_popup_permission_title)
        compose.onNodeWithContentDescription(context.getString(R.string.background_settings_open_description, popupTitle))
            .performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("查看机型设置说明").performScrollTo().performClick()
        compose.onNodeWithText(context.getString(CommonR.string.compatibility_popup_xiaomi))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("开始服务").assertIsDisplayed()
        compose.onNodeWithText("检查系统权限").assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(1, fixture.dismisses)
            assertEquals(0, fixture.confirms)
            assertEquals(0, fixture.settingsActions)
        }
    }

    private fun currentDialog(): ComponentDialog {
        val view = requireNotNull(compose.onNode(isDialog()).fetchSemanticsNode().layoutInfo.view)
        return compose.runOnIdle {
            val provider = generateSequence(view) { it.parent as? View }
                .filterIsInstance<DialogWindowProvider>().first()
            provider.window.callback as ComponentDialog
        }
    }

    private fun settingsRow(item: PermissionGuideItem): SemanticsNodeInteraction {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return compose.onNodeWithContentDescription(context.getString(
            R.string.background_settings_open_description, context.getString(item.titleRes),
        ))
    }

    private fun resourceText(resource: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

    private fun render(fixture: Fixture) {
        compose.runOnIdle {
            fixture.owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
        }
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalLifecycleOwner provides fixture.owner,
                LocalDensity provides Density(density.density, fixture.fontScale),
            ) {
                LongCareTheme {
                    var visible by remember { mutableStateOf(false) }
                    TextButton(onClick = { visible = true }) {
                        Text(stringResource(R.string.background_settings_action))
                    }
                    if (visible) {
                        BackgroundSettingsDialog(
                            initialItems = fixture.items,
                            refreshItems = { fixture.items },
                            onItemAction = {
                                fixture.settingsActions++
                                fixture.settingsOpenSuccess
                            },
                            dismissLabelRes = if (fixture.showConfirm) R.string.common_cancel else R.string.common_close,
                            onConfirm = if (fixture.showConfirm) {
                                {
                                    fixture.confirms++
                                    visible = false
                                }
                            } else null,
                            onCheckSystemPermissions = if (fixture.showSystemCheck) {
                                {
                                    fixture.systemChecks++
                                    visible = false
                                }
                            } else null,
                            onDismiss = {
                                fixture.dismisses++
                                visible = false
                            },
                        )
                    }
                }
            }
        }
    }

    private class Fixture {
        lateinit var owner: TestOwner
        var items = listOf(guide(CommonR.string.compatibility_status_not_exempt))
        var settingsActions = 0
        var settingsOpenSuccess = true
        var dismisses = 0
        var showSystemCheck = false
        var systemChecks = 0
        var showConfirm = false
        var confirms = 0
        var fontScale = 1f
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private companion object {
        fun fourGuides() = listOf(
            guide(CommonR.string.compatibility_status_not_exempt),
            guide(CommonR.string.compatibility_status_background_unknown).copy(
                id = PermissionGuideId.BACKGROUND_ACTIVITY,
                titleRes = CommonR.string.compatibility_battery_title_power_strategy,
                messageRes = CommonR.string.compatibility_battery_xiaomi,
            ),
            guide(null).copy(
                id = PermissionGuideId.AUTO_START,
                titleRes = CommonR.string.compatibility_auto_start_title,
                messageRes = CommonR.string.compatibility_auto_start_xiaomi,
            ),
            guide(null).copy(
                id = PermissionGuideId.MANUFACTURER_POPUP,
                titleRes = CommonR.string.compatibility_popup_permission_title,
                messageRes = CommonR.string.compatibility_popup_xiaomi,
            ),
        )

        fun guide(status: Int?) = PermissionGuideItem(
            id = PermissionGuideId.BATTERY_OPTIMIZATION,
            titleRes = CommonR.string.compatibility_battery_optimization_title,
            messageRes = CommonR.string.compatibility_battery_optimization_message,
            settingsIntent = Intent("com.ytone.longcare.test.BACKGROUND_SETTINGS"),
            statusRes = status,
        )
    }
}
