package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

data class PendingNfcData(
    val orderKey: OrderKey,
    val signInMode: SignInMode,
    val endOderInfo: EndOderInfo?,
    val tagId: String,
    val longitude: String,
    val latitude: String
)

sealed class NfcSignInUiState {
    data object Loading : NfcSignInUiState()
    data class Success(
        val endOrderSuccessData: EndOrderSuccessData? = null
    ) : NfcSignInUiState()

    data class Error(
        val message: String,
        val occurrenceId: Long = System.nanoTime()
    ) : NfcSignInUiState()
    data object Initial : NfcSignInUiState()
    data class ShowConfirmDialog(
        val message: String,
        val endOrderParams: EndOrderParams
    ) : NfcSignInUiState()
}

data class EndOrderSuccessData(
    val trueServiceTime: Int
)

data class EndOrderParams(
    val orderKey: OrderKey,
    val nfcDeviceId: String,
    val porjectIdList: List<Int>,
    val beginImgList: List<String>,
    val endImageList: List<String>,
    val centerImgList: List<String>,
    val longitude: String,
    val latitude: String,
    val endType: Int
)
