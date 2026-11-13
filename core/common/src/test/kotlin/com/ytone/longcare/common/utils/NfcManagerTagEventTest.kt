package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
import androidx.activity.ComponentActivity
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.event.ScanSource
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.After
import org.junit.Assert.*
import io.mockk.unmockkAll
import android.nfc.NfcAdapter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NfcManagerTagEventTest {

    private val appEventBus = mockk<AppEventBus>(relaxed = true)
    private lateinit var activity: Activity
    private lateinit var nfcManager: NfcManager
    private lateinit var controller: ActivityController<ComponentActivity>

    @Before
    fun setUp() {
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller.get()
        nfcManager = NfcManager(appEventBus)

        mockkObject(NfcForegroundDispatchDelegate)
        mockkObject(NfcEnableDialogDelegate)
        mockkObject(NfcUtils)

        every { NfcForegroundDispatchDelegate.isResumed(any()) } returns false
        every { NfcEnableDialogDelegate.dismiss(any()) } returns null
    }

    @Test
    fun `intent is consumed and emits only the current page card event`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk {
            every { id } returns byteArrayOf(0x01, 0x0A)
        }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"

        nfcManager.enableNfcForActivity(activity, "page-1")
        val intent = Intent("android.nfc.action.TAG_DISCOVERED").putExtra(NfcAdapter.EXTRA_ID, byteArrayOf(1, 10))
        nfcManager.handleNfcIntent(activity, intent)
        advanceUntilIdle()

        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertFalse(intent.hasExtra(NfcAdapter.EXTRA_ID))
        assertEquals(Intent.ACTION_MAIN, activity.intent.action)
        coVerify(exactly = 1) { appEventBus.send(AppEvent.TagScanned("010A", ScanSource.SYSTEM_NFC, "page-1")) }
        nfcManager.handleNfcIntent(activity, intent)
        advanceUntilIdle()
        coVerify(exactly = 1) { appEventBus.send(any()) }
    }

    @After fun cleanup() { controller.pause().stop().destroy(); unmockkAll() }

    @Test fun `inactive and restored intents are consumed without waiting for a future page`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk { every { id } returns byteArrayOf(1, 10) }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"
        val inactive = Intent(NfcAdapter.ACTION_TECH_DISCOVERED)
        nfcManager.handleNfcIntent(activity, inactive)
        nfcManager.enableNfcForActivity(activity, "page-2")
        nfcManager.handleNfcIntent(activity, inactive)
        val restored = Intent(NfcAdapter.ACTION_TECH_DISCOVERED)
        nfcManager.handleNfcIntent(activity, restored, dispatchAllowed = false)
        advanceUntilIdle()
        assertEquals(Intent.ACTION_MAIN, restored.action)
        coVerify(exactly = 0) { appEventBus.send(any()) }
    }

    @Test fun `old page cleanup cannot disable the replacement page and new scans of the same card work`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk { every { id } returns byteArrayOf(1, 10) }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"
        nfcManager.enableNfcForActivity(activity, "old")
        nfcManager.enableNfcForActivity(activity, "new")
        nfcManager.disableNfcForActivity(activity, "old")
        repeat(2) { nfcManager.handleNfcIntent(activity, Intent(NfcAdapter.ACTION_TECH_DISCOVERED)) }
        advanceUntilIdle()
        coVerify(exactly = 2) { appEventBus.send(AppEvent.TagScanned("010A", ScanSource.SYSTEM_NFC, "new")) }
    }

    @Test fun `fresh Intent delivered while stopped waits for its current page to start`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk { every { id } returns byteArrayOf(1, 10) }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"
        nfcManager.enableNfcForActivity(activity, "page")
        controller.pause().stop()
        nfcManager.handleNfcIntent(activity, Intent(NfcAdapter.ACTION_TECH_DISCOVERED))
        advanceUntilIdle()
        coVerify(exactly = 0) { appEventBus.send(any()) }
        controller.restart().start().resume()
        advanceUntilIdle()
        coVerify(exactly = 1) { appEventBus.send(AppEvent.TagScanned("010A", ScanSource.SYSTEM_NFC, "page")) }
    }

    @Test fun `replacing a stopped page cancels pending delivery instead of forwarding it to the next page`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk { every { id } returns byteArrayOf(1, 10) }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"
        nfcManager.enableNfcForActivity(activity, "old")
        controller.pause().stop()
        nfcManager.handleNfcIntent(activity, Intent(NfcAdapter.ACTION_TECH_DISCOVERED))
        nfcManager.enableNfcForActivity(activity, "new")
        controller.restart().start().resume()
        advanceUntilIdle()
        coVerify(exactly = 0) { appEventBus.send(any()) }
    }
}
