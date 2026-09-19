package com.ytone.longcare.features.servicecountdown.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServiceCountdownDialogsTest {
    @get:Rule
    val compose = createComposeRule()

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
