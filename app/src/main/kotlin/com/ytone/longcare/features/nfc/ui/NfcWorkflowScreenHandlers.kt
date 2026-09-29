package com.ytone.longcare.features.nfc.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper.openLocationSettings
import com.ytone.longcare.common.utils.locationPermissionPurposeNotice
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.vm.LocationRequestResult
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CancellationException

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
internal fun rememberNfcLocationRequest(
    context: Context,
    orderKey: OrderKey,
    nfcViewModel: NfcWorkflowViewModel,
): suspend () -> LocationRequestResult {
    var showLocationOnlyPurposeNotice by remember { mutableStateOf(false) }
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
                nfcViewModel.acquireLocation()
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

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {
            nfcViewModel.notifyLocationPermissionGranted()
            nfcViewModel.resumePendingPermissionScan {
                getCurrentLocationCoordinates()
            }
        },
        onPermissionDenied = {
            nfcViewModel.clearPendingPermissionScan()
        }
    )

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

    return getCurrentLocationCoordinates
}

internal fun handleNfcSuccessAction(
    signInMode: SignInMode,
    orderKey: OrderKey,
    endOderInfo: EndOderInfo?,
    uiState: NfcSignInUiState,
    nfcViewModel: NfcWorkflowViewModel,
    actions: NfcWorkflowActions,
) {
    when (signInMode) {
        SignInMode.START_ORDER -> {
            actions.onNavigateToIdentification(orderKey)
        }

        SignInMode.END_ORDER -> {
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
