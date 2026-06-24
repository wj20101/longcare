package com.ytone.longcare.common.utils

import android.content.IntentFilter
import android.nfc.NfcAdapter

object NfcIntentActions {

    fun isSupportedTagAction(action: String?): Boolean =
        action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == legacyTagDiscoveredAction

    fun createLegacyTagDiscoveredFilter(): IntentFilter =
        IntentFilter(legacyTagDiscoveredAction)

    @Suppress("DEPRECATION")
    private val legacyTagDiscoveredAction: String = NfcAdapter.ACTION_TAG_DISCOVERED
}
