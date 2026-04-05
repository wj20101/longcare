package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

internal suspend fun handleTagScanned(
    event: AppEvent.TagScanned,
    currentState: NfcSignInUiState,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    onLocationRequest: suspend () -> LocationRequestResult,
    onLocationError: (String) -> Unit,
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit,
) {
    if (currentState is NfcSignInUiState.Success) return

    val locationResult = onLocationRequest()
    val (longitude, latitude) = when (locationResult) {
        is LocationRequestResult.Coordinates -> locationResult.longitude to locationResult.latitude
        is LocationRequestResult.Error -> {
            onLocationError(locationResult.message)
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
    event is AppEvent.ReaderConnectionChanged && event.connected -> ReaderUiState.Ready
    event is AppEvent.ReaderConnectionChanged && !event.connected -> ReaderUiState.Disconnected
    event is AppEvent.ReaderError -> ReaderUiState.DeviceError(event.message)
    else -> currentReaderState
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
