package com.ytone.longcare.common.utils

import android.nfc.NfcAdapter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcIntentActionsTest {

    @Test
    fun `supported actions include ndef tech and legacy tag actions`() {
        assertTrue(NfcIntentActions.isSupportedTagAction(NfcAdapter.ACTION_NDEF_DISCOVERED))
        assertTrue(NfcIntentActions.isSupportedTagAction(NfcAdapter.ACTION_TECH_DISCOVERED))
        assertTrue(NfcIntentActions.isSupportedTagAction("android.nfc.action.TAG_DISCOVERED"))
    }

    @Test
    fun `unsupported actions are rejected`() {
        assertFalse(NfcIntentActions.isSupportedTagAction(null))
        assertFalse(NfcIntentActions.isSupportedTagAction("android.intent.action.VIEW"))
    }
}
