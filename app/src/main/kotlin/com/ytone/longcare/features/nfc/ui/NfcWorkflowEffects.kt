package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.features.nfc.vm.LocationRequestResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

@Composable
internal fun NfcWorkflowEffects(
    activity: Activity?,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    uiState: NfcSignInUiState,
    nfcViewModel: NfcWorkflowViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    onLocationRequest: suspend () -> LocationRequestResult,
    onEntryLocationPrepare: () -> Unit
) {
    LaunchedEffect(activity) {
        if (activity != null) {
            nfcViewModel.startActiveScanSource(activity)
            nfcViewModel.refreshExternalReaderReadyState()
        }
    }

    LaunchedEffect(orderKey, signInMode, endOderInfo) {
        nfcViewModel.observeScanEvents(
            orderKey = orderKey,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            onLocationRequest = { onLocationRequest() },
        )
    }

    LaunchedEffect(orderKey, signInMode) {
        onEntryLocationPrepare()
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.let { nfcViewModel.stopActiveScanSource(it) }
        }
    }

    LaunchedEffect(uiState, signInMode) {
        if (signInMode == SignInMode.END_ORDER && uiState is NfcSignInUiState.Success) {
            locationTrackingViewModel.onStopClicked()
        }
    }
}
