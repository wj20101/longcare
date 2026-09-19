package com.ytone.longcare.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.regex.Pattern

/** Run on a dedicated emulator with CAMERA revoked before starting instrumentation. */
class AssistantCameraPermissionTest {
    @get:Rule val compose = createAndroidComposeRule<AssistantActivity>()

    @Test fun denied_camera_can_be_granted_in_settings_and_capture_returns_to_assistant() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val packageName = compose.activity.packageName
        assertEquals(PackageManager.PERMISSION_DENIED,
            ContextCompat.checkSelfPermission(compose.activity, Manifest.permission.CAMERA))
        compose.waitForIdle()
        if (compose.onAllNodesWithText("同意并继续").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("同意并继续").performScrollTo().performClick()
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("标准相机 · 水印与压缩").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("标准相机 · 水印与压缩").performScrollTo().performClick()
        compose.onNodeWithContentDescription("拍照").assertDoesNotExist()
        compose.onNodeWithText("申请相机权限").performClick()
        // The educational dialog is optional to dismiss and must precede the system prompt.
        compose.onNodeWithText("继续").performClick()
        val deny = device.wait(Until.findObject(By.res(Pattern.compile(".*:id/permission_deny_button"))), 10_000)
        assertNotNull("Camera runtime permission dialog", deny)
        deny.click()
        compose.waitForIdle()
        compose.onNodeWithText("申请相机权限").assertExists()
        compose.onNodeWithContentDescription("拍照").assertDoesNotExist()

        compose.activityRule.scenario.onActivity {
            it.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000))
        // Same runtime grant as changing the switch in Settings; does not deliver an ActivityResult.
        device.executeShellCommand("pm grant $packageName android.permission.CAMERA")
        device.pressBack()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithContentDescription("拍照").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("申请相机权限").assertDoesNotExist()
        compose.onNodeWithContentDescription("拍照").performClick()
        compose.waitUntil(20_000) {
            compose.onAllNodesWithContentDescription("采集照片预览").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("NFC / R65C 读卡验证").assertExists()
        compose.onNodeWithText("清空结果").performScrollTo().performClick()
        compose.onNodeWithContentDescription("采集照片预览").assertDoesNotExist()
    }
}
