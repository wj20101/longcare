package com.ytone.longcare.assistant

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.ytone.longcare.features.login.vm.LoginUiState
import com.ytone.longcare.features.login.vm.SendSmsCodeUiState
import com.ytone.longcare.theme.LongCareTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AssistantEntryScreensTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantActivity>()

    private fun content(body: @Composable () -> Unit) {
        compose.activityRule.scenario.onActivity { activity ->
            activity.setContent { LongCareTheme { body() } }
        }
        compose.waitForIdle()
    }

    @Test fun privacy_requires_explicit_choice() {
        var accepted = 0
        var rejected = 0
        content { AssistantPrivacyScreen({ accepted++ }, { rejected++ }) }
        compose.onNodeWithText("验证助手隐私说明").assertExists()
        compose.onNodeWithText("手机号码").assertDoesNotExist()
        compose.onNodeWithText("不同意并退出").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, accepted); assertEquals(1, rejected) }
        compose.onNodeWithText("同意并继续").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, accepted) }
    }

    @Test fun login_input_feedback_loading_and_countdown_are_rendered() {
        var phone by mutableStateOf("")
        var code by mutableStateOf("")
        var countdown by mutableIntStateOf(0)
        var login by mutableStateOf<LoginUiState>(LoginUiState.Idle)
        var submitted: Pair<String, String>? = null
        var sent = 0
        content {
            AssistantLoginContent(phone, code, { phone = it }, { code = it }, login,
                SendSmsCodeUiState.Idle, countdown, "测试失败提示", { sent++ },
                { submitted = phone to code }, {})
        }
        compose.onNodeWithText("手机号码").performTextInput("13800138000")
        compose.onNodeWithText("短信验证码").performTextInput("123456")
        compose.onNodeWithText("发送验证码").performScrollTo().performClick()
        compose.onNode(hasText("助手登录") and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, sent); assertEquals(phone to code, submitted) }
        compose.onNodeWithText("测试失败提示").assertExists()
        compose.runOnIdle { countdown = 59; login = LoginUiState.Loading }
        compose.onNodeWithText("59 秒后重发").assertIsNotEnabled()
        compose.onNodeWithText("正在登录…").assertIsNotEnabled()
        compose.runOnIdle { countdown = 0; login = LoginUiState.Error("retry") }
        compose.onNodeWithText("发送验证码").assertIsEnabled()
        compose.onNode(hasText("助手登录") and hasClickAction()).assertIsEnabled()
    }

    @Test fun invalid_orders_show_error_and_never_start_verification() {
        val started = mutableListOf<Long>()
        content { AssistantOrderScreen({}, started::add) }
        val input = compose.onNodeWithText("服务订单 ID（1–2147483647）")
        compose.onNodeWithText("开始验证").assertIsNotEnabled()
        listOf("0", "-1", "abc", "2147483648", "999999999999999999999999", "").forEach {
            input.performTextReplacement(it)
            compose.onNodeWithText("请输入 1–2147483647 范围内的整数订单 ID。").assertExists()
            compose.onNodeWithText("开始验证").assertIsNotEnabled().performClick()
        }
        compose.runOnIdle { assertTrue(started.isEmpty()) }
        input.performTextReplacement("2147483647")
        compose.onNodeWithText("开始验证").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(2147483647L), started) }
    }

    @Test fun home_displays_photo_metrics_and_outcome_and_clears_them() {
        val metrics = compose.activity.getString(R.string.assistant_face_metrics, 640, 480, 12345L)
        var result by mutableStateOf("人脸验证成功\n$metrics")
        content { AssistantHomeScreen(true, result, "", { result = "" }, {}, {}, {}) }
        compose.onNodeWithText("人脸验证成功\n提交图片：640 × 480 px，12345 字节").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("清空结果").performScrollTo().performClick()
        compose.onNodeWithText("最近验证结果").assertDoesNotExist()
    }
}
