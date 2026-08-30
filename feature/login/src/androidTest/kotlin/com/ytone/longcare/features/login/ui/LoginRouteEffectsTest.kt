package com.ytone.longcare.features.login.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.features.login.vm.LoginUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginRouteEffectsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun success_transition_invokes_success_action_once() {
        var loginState by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        var successCount = 0
        composeRule.setContent {
            LoginSuccessEffect(
                loginState = loginState,
                onLoginSuccess = { successCount++ },
            )
        }

        composeRule.runOnIdle {
            loginState = LoginUiState.Loading
        }
        composeRule.waitForIdle()
        assertEquals(0, successCount)

        composeRule.runOnIdle {
            loginState = LoginUiState.Error("登录失败")
        }
        composeRule.waitForIdle()
        assertEquals(0, successCount)

        composeRule.runOnIdle {
            loginState = LoginUiState.Success
        }
        composeRule.waitForIdle()
        assertEquals(1, successCount)

        composeRule.runOnIdle {
            loginState = LoginUiState.Success
        }
        composeRule.waitForIdle()
        assertEquals(1, successCount)
    }
}
