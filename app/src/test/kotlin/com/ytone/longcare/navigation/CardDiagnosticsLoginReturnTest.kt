package com.ytone.longcare.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.feature.login.api.LoginFeatureActions
import com.ytone.longcare.features.login.ui.LoginScreenContent
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.features.login.vm.StartConfigUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardDiagnosticsLoginReturnTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun actualLoginFormSurvivesDiagnosticsPushAndBack() {
        compose.setContent {
            LongCareTheme {
                AppNavigationHost(LoginRoute, "anonymous") { nav ->
                    AppEntryProviderBuilder().apply {
                        destination<LoginRoute> {
                            LoginScreenContent(
                                actions = LoginFeatureActions({}, { _, _ -> }),
                                loginState = LoginUiState.Idle,
                                sendSmsState = SendSmsCodeUiState.Idle,
                                startConfigState = StartConfigUiState.Idle,
                                countdownSeconds = 0,
                                onOpenCardDiagnostics = { nav.navigate(CardDiagnosticsRoute) },
                                onSendCodeClick = {},
                                onLoginClick = { _, _ -> },
                            )
                        }
                        destination<CardDiagnosticsRoute> { entry ->
                            Button(onClick = { nav.forEntry(entry.id).popBackStack() }) {
                                Text("返回登录")
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithTag("login_phone_input").performTextInput("13800138000")
        compose.onNodeWithTag("login_verification_code_input").performTextInput("123456")
        compose.onNodeWithTag("login_main_logo").performTouchInput { longClick() }
        compose.onNodeWithText("打开").performClick()
        compose.onNodeWithText("返回登录").performClick()
        compose.onNodeWithTag("login_phone_input").assertTextContains("13800138000")
        compose.onNodeWithTag("login_verification_code_input").assertTextContains("123456")
    }
}
