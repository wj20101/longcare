package com.ytone.longcare.features.login.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LoginCardDiagnosticsEntryTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val loginState = mutableStateOf<LoginUiState>(LoginUiState.Idle)
    private var entered = 0
    private var haptics = 0

    private fun show(withEntry: Boolean = true) {
        compose.setContent {
            LongCareTheme {
                CompositionLocalProvider(LocalHapticFeedback provides object : HapticFeedback {
                    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                        haptics++
                    }
                }) {
                    LoginScreenContent(
                        actions = LoginFeatureActions({}, { _, _ -> }),
                        loginState = loginState.value,
                        sendSmsState = SendSmsCodeUiState.Idle,
                        startConfigState = StartConfigUiState.Idle,
                        countdownSeconds = 0,
                        onOpenCardDiagnostics = if (withEntry) ({ entered++ }) else null,
                        onSendCodeClick = {},
                        onLoginClick = { _, _ -> },
                    )
                }
            }
        }
    }

    private fun longPress() = compose.onNodeWithTag("login_main_logo").performTouchInput { longClick() }
    private fun noDialog() = compose.onNodeWithText("打开助手").assertDoesNotExist()

    @Test fun longPressAsksWithoutHapticsAndConfirmEntersOnce() {
        show()
        longPress()
        compose.onAllNodesWithText("打开助手").assertCountEquals(1)
        compose.onNodeWithText("可进行 NFC 和 R65C 读卡检测，检测数据仅在本机显示。").assertExists()
        assertEquals(0, entered)
        assertEquals(0, haptics)
        compose.onNodeWithText("打开").performClick()
        noDialog()
        assertEquals(1, entered)
    }

    @Test fun shortPressDoesNothing() {
        show()
        compose.onNodeWithTag("login_main_logo").performTouchInput { click() }
        noDialog()
        assertEquals(0, entered)
        assertEquals(0, haptics)
    }

    @Test fun pressingLogoDoesNotChangeItsPixels() {
        show()
        val logo = compose.onNodeWithTag("login_main_logo")
        val before = logo.captureToImage().asAndroidBitmap()
        logo.performTouchInput { down(center); advanceEventTime(100) }
        val pressed = logo.captureToImage().asAndroidBitmap()
        assertTrue("Logo must not draw a ripple or press highlight", before.sameAs(pressed))
        logo.performTouchInput { up() }
        noDialog()
    }

    @Test fun movingOutsideBeforeTimeoutCancelsLongPress() {
        show()
        compose.onNodeWithTag("login_main_logo").performTouchInput {
            down(center)
            moveTo(Offset(-100f, -100f), delayMillis = 50)
            advanceEventTime(1000)
            up()
        }
        noDialog()
        assertEquals(0, haptics)
    }

    @Test fun sustainedPressOnlyShowsOneDialog() {
        show()
        compose.onNodeWithTag("login_main_logo").performTouchInput { longClick(durationMillis = 2500) }
        compose.onAllNodesWithText("打开助手").assertCountEquals(1)
        assertEquals(0, entered)
        assertEquals(0, haptics)
    }

    @Test fun cancelRequiresNewLongPressAndKeepsForm() {
        show()
        compose.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        longPress()
        compose.onNodeWithText("取消").performClick()
        noDialog()
        compose.onNodeWithTag("login_phone_input").assertTextContains("13800138000")
        longPress()
        compose.onNodeWithText("打开助手").assertExists()
        assertEquals(0, entered)
    }

    @Test fun loadingDisablesEntryAndDismissesConfirmation() {
        show()
        longPress()
        compose.runOnIdle { loginState.value = LoginUiState.Loading }
        noDialog()
        longPress()
        noDialog()
        assertEquals(0, entered)
    }

    @Test fun missingCallbackDoesNotEnableEntry() {
        show(withEntry = false)
        longPress()
        noDialog()
        assertEquals(0, entered)
    }

    @Test fun backgroundCancelsAndResumeDoesNotReopen() {
        show()
        longPress()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        noDialog()
        assertEquals(0, entered)
        longPress()
        compose.onNodeWithText("打开助手").assertExists()
    }

    @Test fun systemBackCancels() {
        show()
        longPress()
        compose.runOnIdle {
            (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        noDialog()
        assertEquals(0, entered)
    }

    @Test fun smallLogoAndBackgroundDoNotOpenEntry() {
        show()
        compose.onNodeWithContentDescription("应用小图标").performTouchInput { longClick() }
        noDialog()
        compose.onNodeWithContentDescription("登录背景").performTouchInput {
            longClick(Offset(10f, center.y))
        }
        noDialog()
        assertEquals(0, haptics)
    }

    @Test fun agreementDialogDisablesLogoUntilDismissed() {
        show()
        compose.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        compose.onNodeWithText("发送验证码").performClick()
        compose.onNodeWithTag("login_main_logo").assertIsNotEnabled()
        noDialog()
        assertEquals(0, entered)
    }
}
