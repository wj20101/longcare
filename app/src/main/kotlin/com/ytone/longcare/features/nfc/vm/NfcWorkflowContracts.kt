package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

enum class ScanMode {
    SYSTEM_NFC,
    EXTERNAL_RFID,
}

sealed class ReaderUiState {
    data object NotRequired : ReaderUiState()
    data object Disconnected : ReaderUiState()
    data object Ready : ReaderUiState()
    data object Reading : ReaderUiState()
    data class DeviceError(val message: String) : ReaderUiState()
}

internal fun selectScanMode(isNfcSupported: Boolean): ScanMode =
    if (isNfcSupported) ScanMode.SYSTEM_NFC else ScanMode.EXTERNAL_RFID

data class PendingNfcData(
    val orderKey: OrderKey,
    val signInMode: SignInMode,
    val endOderInfo: EndOderInfo?,
    val tagId: String,
    val longitude: String,
    val latitude: String,
)

data class PendingNfcScan(
    val orderKey: OrderKey,
    val signInMode: SignInMode,
    val endOderInfo: EndOderInfo?,
    val tagId: String,
)

enum class NfcLoadingReason {
    CARD_RECOGNIZED_FETCHING_LOCATION,
    WAITING_FOR_LOCATION_PERMISSION,
    FETCHING_LOCATION,
    SUBMITTING,
}

sealed class NfcSignInUiState {
    data class Loading(
        val reason: NfcLoadingReason = NfcLoadingReason.SUBMITTING
    ) : NfcSignInUiState()
    data class Success(val endOrderSuccessData: EndOrderSuccessData? = null) : NfcSignInUiState()
    data class Error(
        val message: String,
        val occurrenceId: Long = System.nanoTime(),
        val buglyReported: Boolean = false,
    ) : NfcSignInUiState()
    data object Initial : NfcSignInUiState()
    data class ShowConfirmDialog(
        val message: String,
        val endOrderParams: EndOrderParams,
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

sealed class LocationRequestResult {
    data class Coordinates(val longitude: String, val latitude: String) : LocationRequestResult()
    data class Error(
        val message: String,
        val buglyReported: Boolean = false,
    ) : LocationRequestResult()
    data object PermissionRequired : LocationRequestResult()
}
