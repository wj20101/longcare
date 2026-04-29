package com.ytone.longcare.features.nfc.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper.openLocationSettings
import com.ytone.longcare.common.utils.locationPermissionPurposeNotice
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.vm.LocationRequestResult
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class NfcWorkflowLocationHandlers(
    val startTrackingWithPermission: (onReady: () -> Unit) -> Unit,
    val getCurrentLocationCoordinates: suspend () -> LocationRequestResult,
    val prepareLocationOnEntry: () -> Unit
)

internal fun mapNfcSignInState(uiState: NfcSignInUiState): SignInState {
    return when (uiState) {
        is NfcSignInUiState.Loading -> SignInState.IDLE
        is NfcSignInUiState.Success -> SignInState.SUCCESS
        is NfcSignInUiState.Error -> SignInState.FAILURE
        is NfcSignInUiState.Initial -> SignInState.IDLE
        is NfcSignInUiState.ShowConfirmDialog -> SignInState.IDLE
    }
}

internal fun resolveNfcWorkflowTitleRes(signInMode: SignInMode): Int {
    return when (signInMode) {
        SignInMode.START_ORDER -> R.string.nfc_sign_in_title
        SignInMode.END_ORDER -> R.string.nfc_sign_out_title
    }
}

internal fun buildNfcWorkflowBackAction(
    signInMode: SignInMode,
    signInState: SignInState,
    actions: NfcWorkflowActions
): () -> Unit {
    return {
        if (signInMode == SignInMode.END_ORDER && signInState == SignInState.SUCCESS) {
            actions.onNavigateHomeAndClearStack()
        } else {
            actions.onNavigateBack()
        }
    }
}

@Composable
internal fun rememberNfcWorkflowLocationHandlers(
    context: Context,
    orderKey: OrderKey,
    nfcViewModel: NfcWorkflowViewModel,
    locationTrackingViewModel: LocationTrackingViewModel
): NfcWorkflowLocationHandlers {
    val coroutineScope = rememberCoroutineScope()
    var showTrackingLocationPurposeNotice by remember { mutableStateOf(false) }
    var showLocationOnlyPurposeNotice by remember { mutableStateOf(false) }
    var pendingTrackingReadyAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val requestLocationPermissionOnly: () -> Unit = {
        if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
            showLocationOnlyPurposeNotice = true
        }
    }

    val getCurrentLocationCoordinates: suspend () -> LocationRequestResult = {
        try {
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                requestLocationPermissionOnly()
                LocationRequestResult.PermissionRequired
            } else if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                openLocationSettings(context)
                LocationRequestResult.Error("请开启定位服务以获取位置信息")
            } else {
                val (longitude, latitude) = nfcViewModel.getCurrentLocationCoordinates()
                if (longitude.isBlank() || latitude.isBlank()) {
                    LocationRequestResult.Coordinates("", "")
                } else {
                    LocationRequestResult.Coordinates(longitude, latitude)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationRequestResult.Error("无法获取位置信息，请稍后重试")
        }
    }

    val trackingPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {
            locationTrackingViewModel.onStartClicked(orderKey)
            pendingTrackingReadyAction?.invoke()
            pendingTrackingReadyAction = null
        },
        onPermissionDenied = {
            pendingTrackingReadyAction = null
        }
    )

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {
            coroutineScope.launch {
                getCurrentLocationCoordinates()
            }
            nfcViewModel.resumePendingPermissionScan {
                getCurrentLocationCoordinates()
            }
        },
        onPermissionDenied = {
            nfcViewModel.clearPendingPermissionScan()
        }
    )

    val prepareLocationOnEntry: () -> Unit = {
        when {
            !UnifiedPermissionHelper.hasLocationPermission(context) -> {
                showLocationOnlyPurposeNotice = true
            }
            !UnifiedPermissionHelper.isLocationServiceEnabled(context) -> {
                openLocationSettings(context)
            }
            else -> {
                coroutineScope.launch {
                    getCurrentLocationCoordinates()
                }
            }
        }
    }

    val startTrackingWithPermission: (onReady: () -> Unit) -> Unit = { onReady ->
        when {
            !UnifiedPermissionHelper.isLocationServiceEnabled(context) -> openLocationSettings(context)
            UnifiedPermissionHelper.hasLocationPermission(context) -> {
                locationTrackingViewModel.onStartClicked(orderKey)
                onReady()
            }
            else -> {
                pendingTrackingReadyAction = onReady
                showTrackingLocationPurposeNotice = true
            }
        }
    }

    if (showTrackingLocationPurposeNotice) {
        PermissionPurposeDialog(
            notice = locationPermissionPurposeNotice("记录NFC签到后的服务位置"),
            onConfirm = {
                showTrackingLocationPurposeNotice = false
                trackingPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
            },
            onDismiss = {
                pendingTrackingReadyAction = null
                showTrackingLocationPurposeNotice = false
            }
        )
    }

    if (showLocationOnlyPurposeNotice) {
        PermissionPurposeDialog(
            notice = locationPermissionPurposeNotice("获取NFC签到位置"),
            onConfirm = {
                showLocationOnlyPurposeNotice = false
                locationOnlyPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
            },
            onDismiss = { showLocationOnlyPurposeNotice = false }
        )
    }

    return NfcWorkflowLocationHandlers(
        startTrackingWithPermission = startTrackingWithPermission,
        getCurrentLocationCoordinates = getCurrentLocationCoordinates,
        prepareLocationOnEntry = prepareLocationOnEntry
    )
}

internal fun handleNfcSuccessAction(
    signInMode: SignInMode,
    orderKey: OrderKey,
    endOderInfo: EndOderInfo?,
    uiState: NfcSignInUiState,
    nfcViewModel: NfcWorkflowViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    actions: NfcWorkflowActions,
    startTrackingWithPermission: (onReady: () -> Unit) -> Unit
) {
    when (signInMode) {
        SignInMode.START_ORDER -> {
            startTrackingWithPermission {
                actions.onNavigateToIdentification(orderKey)
            }
        }

        SignInMode.END_ORDER -> {
            locationTrackingViewModel.onStopClicked()
            val successState = uiState as? NfcSignInUiState.Success
            val trueServiceTime = successState?.endOrderSuccessData?.trueServiceTime ?: 0
            val serviceCompleteData = nfcViewModel.buildServiceCompleteDataFromCache(
                orderKey = orderKey,
                endOderInfo = endOderInfo,
                trueServiceTime = trueServiceTime
            )
            actions.onNavigateToServiceComplete(orderKey, serviceCompleteData)
        }
    }
}
