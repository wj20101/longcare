package com.ytone.longcare.features.nfc.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
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
    val locationUnavailableMessage = stringResource(R.string.nfc_location_unavailable)
    val locationServiceDisabledMessage = stringResource(R.string.nfc_location_service_disabled)

    val getCurrentLocationCoordinates: suspend () -> LocationRequestResult = {
        try {
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                showLocationOnlyPurposeNotice = true
                LocationRequestResult.PermissionRequired
            } else if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                openLocationSettings(context)
                LocationRequestResult.Error(locationServiceDisabledMessage)
            } else {
                val (longitude, latitude) = nfcViewModel.getCurrentLocationCoordinates()
                toLocationRequestResult(
                    longitude = longitude,
                    latitude = latitude,
                    unavailableMessage = locationUnavailableMessage,
                ).let { result ->
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
                        result.copy(buglyReported = true)
                    } else {
                        result
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
            LocationRequestResult.Error(
                message = locationUnavailableMessage,
                buglyReported = true,
            )
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
            locationTrackingViewModel.startTrackingAfterPermissionGrant(orderKey)
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
                locationTrackingViewModel.startTracking(orderKey)
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
            notice = locationPermissionPurposeNotice(
                stringResource(R.string.nfc_tracking_location_permission_purpose),
            ),
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
            notice = locationPermissionPurposeNotice(
                stringResource(R.string.nfc_location_permission_purpose),
            ),
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
    latitude: String,
    unavailableMessage: String,
): LocationRequestResult {
    return if (longitude.isBlank() || latitude.isBlank()) {
        LocationRequestResult.Error(unavailableMessage)
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
            locationTrackingViewModel.stopTracking()
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
