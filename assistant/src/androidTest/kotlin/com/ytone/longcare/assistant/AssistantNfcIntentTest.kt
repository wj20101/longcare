package com.ytone.longcare.assistant

import android.content.Intent
import android.nfc.NfcAdapter
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantNfcIntentTest : AssistantNfcDeviceTestHost() {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test fun malformed_warm_intents_cannot_open_routes_or_tag_dialogs() {
        device.waitForIdle()
        device.findObject(By.text("同意并继续"))?.click()
        assertTrue(device.wait(Until.hasObject(By.text("NFC / R65C 读卡验证")), 10_000))
        val actions = listOf(
            Intent.ACTION_VIEW,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED,
        )
        actions.forEach { action ->
            deliverNewIntent(
                Intent(action)
                    .putExtra(NfcAdapter.EXTRA_TAG, "not a parcelable tag")
                    .putExtra("orderId", 123)
                    .putExtra(Intent.EXTRA_INTENT, Intent(Intent.ACTION_VIEW))
            )
            device.waitForIdle()
            assertTrue(device.hasObject(By.text("NFC / R65C 读卡验证")))
            assertFalse(device.hasObject(By.text("检测到 NFC 标签")))
            assertFalse(device.hasObject(By.text("手机号码")))
        }
        recreateHost()
        assertTrue(device.wait(Until.hasObject(By.text("NFC / R65C 读卡验证")), 10_000))
    }
}
