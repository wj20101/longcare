package com.ytone.longcare.features.nfc.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.ytone.longcare.R
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
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
    val prepareLocationOnEntry: () -> Unit,
    val isLocationPreparing: Boolean
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

private const val LOCATION_UNAVAILABLE_MESSAGE = "无法获取当前定位，请确认定位权限和定位服务后重试"

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
    var isLocationPreparing by remember { mutableStateOf(false) }

    val getCurrentLocationCoordinates: suspend () -> LocationRequestResult = {
        try {
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                showLocationOnlyPurposeNotice = true
                LocationRequestResult.PermissionRequired
            } else if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                openLocationSettings(context)
                LocationRequestResult.Error("请开启定位服务以获取位置信息")
            } else {
                val (longitude, latitude) = nfcViewModel.getCurrentLocationCoordinates()
                toLocationRequestResult(longitude, latitude).also { result ->
                    if (result is LocationRequestResult.Error) {
                        DiagnosticEventTracker.trackError(
                            category = "nfc_workflow",
                            event = "nfc_location_empty",
                            description = "NFC签到获取定位结果为空",
                            extras = mapOf(
                                "orderId" to orderKey.orderId,
                                "planId" to orderKey.planId,
                                "hasLongitude" to longitude.isNotBlank(),
                                "hasLatitude" to latitude.isNotBlank(),
                            ),
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagnosticEventTracker.trackError(
                category = "nfc_workflow",
                event = "nfc_location_request_exception",
                description = "NFC签到请求定位异常",
                throwable = e,
                extras = mapOf(
                    "orderId" to orderKey.orderId,
                    "planId" to orderKey.planId,
                ),
            )
            LocationRequestResult.Error(LOCATION_UNAVAILABLE_MESSAGE)
        }
    }

    val launchLocationPreparation: () -> Unit = {
        if (!isLocationPreparing) {
            isLocationPreparing = true
            coroutineScope.launch {
                try {
                    getCurrentLocationCoordinates()
                } finally {
                    isLocationPreparing = false
                }
            }
        }
    }

    val trackingPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {
            locationTrackingViewModel.onPermissionGrantedAndStartTracking(orderKey)
            pendingTrackingReadyAction?.invoke()
            pendingTrackingReadyAction = null
        },
        onPermissionDenied = {
            pendingTrackingReadyAction = null
        }
    )

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {
            nfcViewModel.notifyLocationPermissionGranted()
            val resumedPendingScan = nfcViewModel.resumePendingPermissionScan {
                getCurrentLocationCoordinates()
            }
            if (!resumedPendingScan) {
                launchLocationPreparation()
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
                launchLocationPreparation()
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
            onDismiss = {
                showLocationOnlyPurposeNotice = false
                nfcViewModel.clearPendingPermissionScan()
            }
        )
    }

    return NfcWorkflowLocationHandlers(
        startTrackingWithPermission = startTrackingWithPermission,
        getCurrentLocationCoordinates = getCurrentLocationCoordinates,
        prepareLocationOnEntry = prepareLocationOnEntry,
        isLocationPreparing = isLocationPreparing
    )
}

internal fun toLocationRequestResult(
    longitude: String,
    latitude: String
): LocationRequestResult {
    return if (longitude.isBlank() || latitude.isBlank()) {
        LocationRequestResult.Error(LOCATION_UNAVAILABLE_MESSAGE)
    } else {
        LocationRequestResult.Coordinates(longitude, latitude)
    }
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
