package com.ytone.longcare.features.nfc.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.ytone.longcare.R
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
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.core.ui.R as CoreUiR

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
    nfcViewModel: NfcWorkflowViewModel,
): suspend () -> LocationRequestResult {
    var showLocationOnlyPurposeNotice by rememberSaveable { mutableStateOf(false) }
    val locationServiceDisabledMessage = stringResource(R.string.nfc_location_service_disabled)

    val getCurrentLocationCoordinates: suspend () -> LocationRequestResult = {
        if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
            showLocationOnlyPurposeNotice = true
            LocationRequestResult.PermissionRequired
        } else if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
            openLocationSettings(context)
            LocationRequestResult.Error(locationServiceDisabledMessage, reason = LocationFailure.SERVICE_DISABLED)
        } else {
            nfcViewModel.acquireLocation()
        }
    }

    fun resumeLocationRequest() {
        nfcViewModel.resumePendingPermissionScan(getCurrentLocationCoordinates)
    }

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = ::resumeLocationRequest,
        onPermissionDenied = nfcViewModel::clearPendingPermissionScan,
    )
    val locationSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        if (UnifiedPermissionHelper.hasLocationPermission(context)) resumeLocationRequest()
        else nfcViewModel.clearPendingPermissionScan()
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
            },
            onOpenSettings = {
                showLocationOnlyPurposeNotice = false
                try {
                    locationSettingsLauncher.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        "package:${context.packageName}".toUri()))
                } catch (_: ActivityNotFoundException) {
                    nfcViewModel.clearPendingPermissionScan()
                    Toast.makeText(context, CoreUiR.string.permission_settings_unavailable, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    nfcViewModel.clearPendingPermissionScan()
                    Toast.makeText(context, CoreUiR.string.permission_settings_unavailable, Toast.LENGTH_SHORT).show()
                }
            },
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
