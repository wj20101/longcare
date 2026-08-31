package com.ytone.longcare.features.maindashboard.utils

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.debug.NfcTestEntrySession
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NfcTestHelperLifecycleTest {

    private lateinit var controller: ActivityController<ComponentActivity>
    private lateinit var activity: ComponentActivity
    private lateinit var appEventBus: AppEventBus
    private lateinit var nfcManager: NfcManager
    private lateinit var helper: NfcTestHelper

    @Before
    fun setUp() {
        mockkObject(NfcUtils)
        every { NfcUtils.isNfcSupported(any()) } returns true
        every { NfcUtils.isNfcEnabled(any()) } returns true
        every { NfcUtils.enableForegroundDispatch(any(), any()) } returns Unit
        every { NfcUtils.disableForegroundDispatch(any()) } returns Unit
        every { NfcUtils.getTagFromIntent(any()) } returns null

        NfcTestEntrySession.setEnabled(true)
        controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        activity = controller.get()
        appEventBus = spyk(AppEventBus())
        nfcManager = NfcManager(appEventBus)
        helper = NfcTestHelper(
            appEventBus = appEventBus,
            toastHelper = mockk<ToastHelper>(relaxed = true),
            nfcManager = nfcManager,
        )
    }

    @After
    fun tearDown() {
        helper.disable(activity)
        NfcTestEntrySession.resetForTest()
        unmockkObject(NfcUtils)
    }

    @Test
    fun `nfc tag delivered after pause is recognized and opens info dialog`() = runTest {
        val tag = mockk<Tag> {
            every { id } returns byteArrayOf(0x01, 0x2A, 0x7F)
        }
        every { NfcUtils.getTagFromIntent(any()) } returns tag
        every { NfcUtils.bytesToHexString(tag.id) } returns "012A7F"
        helper.enable(activity)

        // Android pauses a resumed singleTop Activity before delivering onNewIntent.
        controller.pause()
        nfcManager.handleNfcIntent(
            activity,
            Intent(NfcAdapter.ACTION_TECH_DISCOVERED),
        )
        advanceUntilIdle()

        coVerify(exactly = 1) {
            appEventBus.send(
                match<AppEvent.NfcIntentReceived> {
                    it.intent.action == NfcAdapter.ACTION_TECH_DISCOVERED
                },
            )
        }
        helper.dialogStateForTest().let { dialogState ->
            assertTrue(dialogState.showDialog)
            assertEquals("012A7F", dialogState.nfcTagId)
        }
    }

    @Test
    fun `explicit disable still rejects a late nfc intent`() = runTest {
        helper.enable(activity)
        helper.disable(activity)

        nfcManager.handleNfcIntent(
            activity,
            Intent(NfcAdapter.ACTION_TECH_DISCOVERED),
        )
        advanceUntilIdle()

        coVerify(exactly = 0) {
            appEventBus.send(any<AppEvent.NfcIntentReceived>())
        }
    }

    private fun NfcTestHelper.dialogStateForTest(): NfcTestDialogState {
        return NfcTestHelper::class.java
            .getDeclaredField("dialogState")
            .apply { isAccessible = true }
            .get(this) as NfcTestDialogState
    }
}
