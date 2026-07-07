package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.ScanSource
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

internal suspend fun handleTagScanned(
    event: AppEvent.TagScanned,
    currentState: NfcSignInUiState,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    onLocationRequest: suspend () -> LocationRequestResult,
    onLocationError: (LocationRequestResult.Error) -> Unit,
    onLocationPermissionRequired: suspend (String) -> Unit = {},
    onLoadingReasonChanged: (NfcLoadingReason) -> Unit = {},
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit,
) {
    if (currentState !is NfcSignInUiState.Initial) return
    if (event.tagId.isBlank()) return

    onLoadingReasonChanged(NfcLoadingReason.CARD_RECOGNIZED_FETCHING_LOCATION)
    val locationResult = onLocationRequest()
    val (longitude, latitude) = when (locationResult) {
        is LocationRequestResult.Coordinates -> locationResult.longitude to locationResult.latitude
        is LocationRequestResult.Error -> {
            onLocationError(locationResult)
            return
        }
        is LocationRequestResult.PermissionRequired -> {
            onLoadingReasonChanged(NfcLoadingReason.WAITING_FOR_LOCATION_PERMISSION)
            onLocationPermissionRequired(event.tagId)
            return
        }
    }

    executeSignInModeAction(
        signInMode = signInMode,
        endOderInfo = endOderInfo,
        tagId = event.tagId,
        longitude = longitude,
        latitude = latitude,
        onStartOrder = onStartOrder,
        onEndOrder = onEndOrder,
    )
}

internal fun reduceReaderUiState(
    currentMode: ScanMode,
    event: AppEvent,
    currentReaderState: ReaderUiState,
): ReaderUiState = when {
    currentMode == ScanMode.SYSTEM_NFC -> ReaderUiState.NotRequired
    event is AppEvent.ReaderConnectionChanged && event.source == activeScanSource(currentMode) && event.connected -> ReaderUiState.Ready
    event is AppEvent.ReaderConnectionChanged && event.source == activeScanSource(currentMode) && !event.connected -> ReaderUiState.Disconnected
    event is AppEvent.ReaderError && event.source == activeScanSource(currentMode) -> ReaderUiState.DeviceError(event.message)
    else -> currentReaderState
}

private fun activeScanSource(mode: ScanMode): ScanSource = when (mode) {
    ScanMode.SYSTEM_NFC -> ScanSource.SYSTEM_NFC
    ScanMode.EXTERNAL_RFID -> ScanSource.EXTERNAL_RFID
}

internal suspend fun executeSignInModeAction(
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    longitude: String,
    latitude: String,
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit,
) {
    when (signInMode) {
        SignInMode.START_ORDER -> onStartOrder(tagId, longitude, latitude)
        SignInMode.END_ORDER -> endOderInfo?.let { onEndOrder(tagId, longitude, latitude, it) }
    }
}
