package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

internal suspend fun handleNfcIntentReceived(
    event: AppEvent.NfcIntentReceived,
    currentState: NfcSignInUiState,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    onLocationRequest: suspend () -> LocationRequestResult,
    onLocationError: (String) -> Unit,
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit
) {
    if (currentState is NfcSignInUiState.Success) {
        return
    }

    val tag = NfcUtils.getTagFromIntent(event.intent) ?: return
    val tagId = NfcUtils.bytesToHexString(tag.id)
    if (tagId.isEmpty()) return

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
        tagId = tagId,
        longitude = longitude,
        latitude = latitude,
        onStartOrder = onStartOrder,
        onEndOrder = onEndOrder
    )
}

internal suspend fun executeSignInModeAction(
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    tagId: String,
    longitude: String,
    latitude: String,
    onStartOrder: suspend (String, String, String) -> Unit,
    onEndOrder: suspend (String, String, String, EndOderInfo) -> Unit
) {
    when (signInMode) {
        SignInMode.START_ORDER -> onStartOrder(tagId, longitude, latitude)
        SignInMode.END_ORDER -> endOderInfo?.let { onEndOrder(tagId, longitude, latitude, it) }
    }
}
