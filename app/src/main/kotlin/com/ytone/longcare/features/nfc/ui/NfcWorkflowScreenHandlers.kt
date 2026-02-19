package com.ytone.longcare.features.nfc.ui

import android.content.Context
import androidx.compose.runtime.Composable
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper.openLocationSettings
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.api.NfcWorkflowActions
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CancellationException

internal data class NfcWorkflowLocationHandlers(
    val startTrackingWithPermission: () -> Unit,
    val getCurrentLocationCoordinates: suspend () -> Pair<String, String>
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
    val trackingPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
    )

    val locationOnlyPermissionLauncher = rememberLocationPermissionLauncher(
        onPermissionGranted = {}
    )

    val requestLocationPermissionOnly: () -> Unit = {
        if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
            locationOnlyPermissionLauncher.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
        }
    }

    val startTrackingWithPermission: () -> Unit = {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(
            context = context,
            permissionLauncher = trackingPermissionLauncher,
            onPermissionGranted = { locationTrackingViewModel.onStartClicked(orderKey) }
        )
    }

    val getCurrentLocationCoordinates: suspend () -> Pair<String, String> = {
        try {
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                requestLocationPermissionOnly()
                Pair("", "")
            } else if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                openLocationSettings(context)
                nfcViewModel.showError("请开启定位服务以获取位置信息")
                Pair("", "")
            } else {
                nfcViewModel.getCurrentLocationCoordinates()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Pair("", "")
        }
    }

    return NfcWorkflowLocationHandlers(
        startTrackingWithPermission = startTrackingWithPermission,
        getCurrentLocationCoordinates = getCurrentLocationCoordinates
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
    startTrackingWithPermission: () -> Unit
) {
    when (signInMode) {
        SignInMode.START_ORDER -> {
            startTrackingWithPermission()
            actions.onNavigateToIdentification(orderKey)
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
