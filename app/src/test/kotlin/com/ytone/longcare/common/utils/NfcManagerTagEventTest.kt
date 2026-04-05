package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
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
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NfcManagerTagEventTest {

    private val appEventBus = mockk<AppEventBus>(relaxed = true)
    private lateinit var activity: Activity
    private lateinit var nfcManager: NfcManager

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        nfcManager = NfcManager(appEventBus)

        mockkObject(NfcForegroundDispatchDelegate)
        mockkObject(NfcEnableDialogDelegate)
        mockkObject(NfcUtils)

        every { NfcForegroundDispatchDelegate.isResumed(any()) } returns false
        every { NfcEnableDialogDelegate.dismiss(any()) } returns null
    }

    @Test
    fun `handleNfcIntent emits legacy intent event and unified tag event`() = runTest {
        every { NfcUtils.getTagFromIntent(any()) } returns mockk {
            every { id } returns byteArrayOf(0x01, 0x0A)
        }
        every { NfcUtils.bytesToHexString(any()) } returns "010A"

        nfcManager.enableNfcForActivity(activity)
        nfcManager.handleNfcIntent(activity, Intent("android.nfc.action.TAG_DISCOVERED"))
        advanceUntilIdle()

        coVerify { appEventBus.send(match<AppEvent.NfcIntentReceived> { true }) }
        coVerify { appEventBus.send(AppEvent.TagScanned("010A", ScanSource.SYSTEM_NFC)) }
    }
}
