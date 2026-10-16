package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ytone.longcare.theme.LongCareTheme
import com.ytone.longcare.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServiceCountdownDialogsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun permission_confirmation_requests_only_after_explicit_click() {
        val actions = mutableListOf<String>()
        renderPermissionDialog(actions)
        compose.runOnIdle { assertEquals(emptyList<String>(), actions) }
        compose.onNodeWithText("允许定位").performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "request"), actions) }
    }

    @Test
    fun permission_settings_is_a_separate_explicit_recovery_action() {
        val actions = mutableListOf<String>()
        renderPermissionDialog(actions)
        val settingsLabel = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getString(R.string.common_go_to_settings)
        compose.onNodeWithText(settingsLabel).performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "settings"), actions) }
    }

    @Test
    fun permission_skip_does_not_request_or_open_settings() {
        val actions = mutableListOf<String>()
        renderPermissionDialog(actions)
        compose.onNodeWithText("暂不设置").performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), actions) }
    }

    private fun renderPermissionDialog(actions: MutableList<String>) {
        compose.setContent {
            LongCareTheme {
                PermissionAlertDialog(
                    visible = true,
                    message = "服务期间需要精确定位",
                    onDismiss = { actions += "dismiss" },
                    confirmLabelRes = R.string.service_countdown_allow_location,
                    onConfirm = { actions += "request" },
                    onOpenAppSettings = { actions += "settings" },
                )
            }
        }
    }

    @Test
    fun confirm_uses_shared_label_and_preserves_callback_order() {
        val actions = mutableListOf<String>()
        compose.setContent {
            LongCareTheme {
                ConfirmEarlyEndServiceDialog(
                    visible = true,
                    onDismiss = { actions += "dismiss" },
                    onConfirm = { actions += "confirm" },
                )
            }
        }
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText("确认").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss", "confirm"), actions) }
    }

    @Test
    fun cancel_uses_shared_label_without_confirming_service_end() {
        val actions = mutableListOf<String>()
        compose.setContent {
            LongCareTheme {
                ConfirmEarlyEndServiceDialog(
                    visible = true,
                    onDismiss = { actions += "dismiss" },
                    onConfirm = { actions += "confirm" },
                )
            }
        }
        compose.onNodeWithText("确认").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf("dismiss"), actions) }
    }
}
