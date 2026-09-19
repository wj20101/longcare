package com.ytone.longcare.assistant

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AssistantNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantActivity>()

    private fun enterHome() {
        compose.waitForIdle()
        if (compose.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("同意并继续").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("默认服务人脸验证").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun home_has_five_entries_and_login_cancel_stays_in_assistant() {
        enterHome()
        listOf("默认服务人脸验证", "NFC / R65C 读卡验证", "标准相机 · 水印与压缩", "备用腾讯人脸验证", "手动人脸采集").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("默认服务人脸验证").performClick()
        compose.onNodeWithText("手机号码").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
    }

    @Test fun local_camera_does_not_require_login_and_back_returns_home() {
        enterHome()
        compose.onNodeWithText("标准相机 · 水印与压缩").performClick()
        compose.onNodeWithText("手机号码").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
    }

    @Test fun tencent_requires_login_and_system_back_returns_home() {
        enterHome()
        compose.onNodeWithText("备用腾讯人脸验证").performScrollTo().performClick()
        compose.onNodeWithText("手机号码").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
    }

    @Test fun nfc_and_manual_capture_return_to_assistant_without_login() {
        enterHome()
        compose.onNodeWithText("NFC / R65C 读卡验证").performScrollTo().performClick()
        compose.onNodeWithText("碰一碰验证").assertExists()
        compose.onNodeWithText("手机号码").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("手动人脸采集").performScrollTo().performClick()
        compose.onNodeWithText("手机号码").assertDoesNotExist()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
    }

    @Test fun nfc_route_survives_recreation_and_leaving_returns_home() {
        enterHome()
        compose.onNodeWithText("NFC / R65C 读卡验证").performScrollTo().performClick()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("碰一碰验证").assertExists()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
    }
}
