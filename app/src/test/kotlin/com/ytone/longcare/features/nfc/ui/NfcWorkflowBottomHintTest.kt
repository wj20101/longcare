package com.ytone.longcare.features.nfc.ui

import com.ytone.longcare.R
import com.ytone.longcare.features.nfc.vm.NfcLoadingReason
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcWorkflowBottomHintTest {

    @Test
    fun `location preparation hint is used when no scan loading is active`() {
        val hintRes = resolveBottomHintRes(
            loadingReason = null,
            isLocationPreparing = true,
            idleBottomHintKey = NfcWorkflowCopyKey.SYSTEM_IDLE_HINT,
        )

        assertEquals(R.string.nfc_location_preparing_hint, hintRes)
    }

    @Test
    fun `scan loading hint has priority over location preparation hint`() {
        val hintRes = resolveBottomHintRes(
            loadingReason = NfcLoadingReason.CARD_RECOGNIZED_FETCHING_LOCATION,
            isLocationPreparing = true,
            idleBottomHintKey = NfcWorkflowCopyKey.SYSTEM_IDLE_HINT,
        )

        assertEquals(R.string.nfc_loading_card_recognized_fetching_location, hintRes)
    }

    @Test
    fun `idle hint is used when not loading or preparing location`() {
        val hintRes = resolveBottomHintRes(
            loadingReason = null,
            isLocationPreparing = false,
            idleBottomHintKey = NfcWorkflowCopyKey.SYSTEM_IDLE_HINT,
        )

        assertEquals(R.string.nfc_sign_in_idle_hint, hintRes)
    }
}
