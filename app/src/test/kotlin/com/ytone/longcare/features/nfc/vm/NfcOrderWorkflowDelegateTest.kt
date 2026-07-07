package com.ytone.longcare.features.nfc.vm

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcOrderWorkflowDelegateTest {

    @Test
    fun `applyUserVisibleError reports fallback error and marks state reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
        var capturedReport: NfcUserVisibleErrorReport? = null

        applyUserVisibleNfcError(
            uiState = uiState,
            message = "请开启定位服务以获取位置信息",
            source = "scan_location_error",
            reporter = { report -> capturedReport = report }
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("请开启定位服务以获取位置信息", error.message)
        assertTrue(error.buglyReported)
        assertEquals("nfc_user_visible_error", capturedReport?.event)
        assertEquals("scan_location_error", capturedReport?.extras?.get("source"))
    }
}
