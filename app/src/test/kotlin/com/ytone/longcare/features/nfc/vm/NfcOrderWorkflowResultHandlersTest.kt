package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class NfcOrderWorkflowResultHandlersTest {

    @Test
    fun `api exception error state is marked as already reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)

        applyOrderApiException(
            exception = ApiResult.Exception(IOException("网络断开")),
            uiState = uiState
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("网络断开", error.message)
        assertTrue(error.buglyReported)
    }

    @Test
    fun `api failure error state is marked as already reported`() {
        val uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)

        applyOrderApiFailure(
            failure = ApiResult.Failure(code = 4001, message = "NFC不匹配"),
            uiState = uiState
        )

        val error = uiState.value as NfcSignInUiState.Error
        assertEquals("NFC不匹配", error.message)
        assertTrue(error.buglyReported)
    }
}
