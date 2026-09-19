package com.ytone.longcare.assistant

import android.nfc.NfcAdapter
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Opt-in hardware tests: temporarily toggles NFC and restores its initial state. No tag is simulated. */
class AssistantNfcHardwareLifecycleTest : AssistantNfcDeviceTestHost() {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var adapter: NfcAdapter? = null
    private var initialEnabled: Boolean? = null

    @Before fun prepareHardware() {
        assumeTrue("Explicit NFC hardware test opt-in required",
            InstrumentationRegistry.getArguments().getString("nfcHardwareTests") == "true")
        adapter = NfcAdapter.getDefaultAdapter(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeTrue("NFC hardware required", adapter != null)
        initialEnabled = adapter!!.isEnabled
        setNfcEnabled(true)
        device.waitForIdle()
        device.findObject(By.text("同意并继续"))?.click()
        awaitText("NFC / R65C 读卡验证")
    }

    @After fun restoreNfc() {
        initialEnabled?.let(::setNfcEnabled)
    }

    @Test fun foreground_dispatch_stops_in_background_and_after_leaving_and_rebinds_after_recreation() {
        assertDispatch(active = false)
        enterNfc()
        assertDispatch(active = true)
        Log.i("NfcLifecycleTest", "NFC foreground dispatch enabled")
        device.pressHome()
        assertDispatch(active = false)
        Log.i("NfcLifecycleTest", "Home disabled foreground dispatch")
        device.executeShellCommand("am start -n com.ytone.longcare.assistant/.AssistantActivity")
        assertDispatch(active = true)
        Log.i("NfcLifecycleTest", "Foreground return enabled dispatch; recreating Activity")
        try {
            recreateHost()
        } catch (failure: AssertionError) {
            Log.e("NfcLifecycleTest", "Activity recreation failed before test teardown", failure)
            throw failure
        }
        awaitText("碰一碰验证")
        assertDispatch(active = true)
        Log.i("NfcLifecycleTest", "Recreated Activity enabled dispatch")
        device.pressBack()
        awaitText("NFC / R65C 读卡验证")
        assertDispatch(active = false)
        Log.i("NfcLifecycleTest", "Leaving NFC route released foreground dispatch")
    }

    @Test fun disabled_nfc_settings_return_refreshes_state_and_enables_dispatch() {
        setNfcEnabled(false)
        enterNfc()
        awaitText(resourceText(R.string.nfc_validation_native_disabled))
        assertDispatch(active = false)
        awaitText(resourceText(R.string.nfc_validation_open_settings)).click()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 10_000))
        setNfcEnabled(true)
        device.pressBack()
        awaitText(resourceText(R.string.nfc_validation_native_ready))
        assertDispatch(active = true)
        device.pressBack()
        awaitText("NFC / R65C 读卡验证")
        assertDispatch(active = false)
    }

    private fun enterNfc() {
        awaitText("NFC / R65C 读卡验证").click()
        awaitText("碰一碰验证")
    }

    private fun setNfcEnabled(enabled: Boolean) {
        if (adapter?.isEnabled == enabled) return
        device.executeShellCommand("cmd nfc ${if (enabled) "enable-nfc" else "disable-nfc"}")
        waitFor("NFC enabled=$enabled") { adapter?.isEnabled == enabled }
    }

    private fun assertDispatch(active: Boolean) {
        // Assert the real NFC service's registration, not a private app flag or mocked adapter.
        waitFor("foreground NFC dispatch active=$active") {
            val registration = device.executeShellCommand("dumpsys nfc").lineSequence()
                .firstOrNull { it.trimStart().startsWith("mOverrideIntent=") }
            checkNotNull(registration) { "NFC service does not expose foreground-dispatch diagnostics" }
            // The NFC process sees a remote BinderProxy, not a string containing the creator package.
            if (active) registration.contains("mOverrideIntent=PendingIntent{") &&
                device.currentPackageName == "com.ytone.longcare.assistant"
            else registration.trim() == "mOverrideIntent=null"
        }
    }

    private fun resourceText(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun awaitText(text: String) = checkNotNull(device.wait(Until.findObject(By.text(text)), 10_000)) {
        "Missing UI text: $text"
    }

    private fun waitFor(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        throw AssertionError("Timed out waiting for $description")
    }
}
