package com.ytone.longcare.features.nfctest.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.nfctest.vm.R65CHidCapturedKeyEvent
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class R65CHidInputCaptureSurfaceTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun enabled_surface_requests_focus() {
        composeRule.setContent {
            LongCareTheme {
                R65CHidInputCaptureSurface(
                    enabled = true,
                    focusRequestToken = 0L,
                    onFocusChanged = {},
                    onKeyCaptured = {},
                    modifier = Modifier.testTag("hid_capture_surface"),
                )
            }
        }

        composeRule.onNodeWithTag("hid_capture_surface").assertIsFocused()
    }

    @Test
    fun disabled_surface_does_not_capture_key_input() {
        val captured = mutableListOf<R65CHidCapturedKeyEvent>()

        composeRule.setContent {
            LongCareTheme {
                R65CHidInputCaptureSurface(
                    enabled = false,
                    focusRequestToken = 0L,
                    onFocusChanged = {},
                    onKeyCaptured = { captured += it },
                    modifier = Modifier.testTag("hid_capture_surface"),
                )
            }
        }

        composeRule.onNodeWithTag("hid_capture_surface").assertIsNotFocused()
        composeRule.onNodeWithTag("hid_capture_surface").performKeyInput {
            pressKey(Key.Enter)
        }

        assertTrue(captured.isEmpty())
    }

    @Test
    fun enabled_surface_forwards_relevant_key_input() {
        val captured = mutableListOf<R65CHidCapturedKeyEvent>()

        composeRule.setContent {
            LongCareTheme {
                R65CHidInputCaptureSurface(
                    enabled = true,
                    focusRequestToken = 0L,
                    onFocusChanged = {},
                    onKeyCaptured = { captured += it },
                    modifier = Modifier.testTag("hid_capture_surface"),
                )
            }
        }

        composeRule.onNodeWithTag("hid_capture_surface").performKeyInput {
            pressKey(Key.Enter)
        }

        assertEquals(1, captured.size)
        assertEquals("\\n", captured.single().displayChar)
    }
}
