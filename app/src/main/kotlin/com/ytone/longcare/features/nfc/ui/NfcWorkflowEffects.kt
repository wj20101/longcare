package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.features.nfc.vm.LocationRequestResult
import com.ytone.longcare.features.nfc.vm.ScanMode
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.platform.nfc.rememberNfcScanSourceUiController

@Composable
internal fun NfcWorkflowEffects(
    activity: Activity?,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    uiState: NfcSignInUiState,
    scanMode: ScanMode,
    nfcViewModel: NfcWorkflowViewModel,
    locationTrackingViewModel: LocationTrackingViewModel,
    onLocationRequest: suspend () -> LocationRequestResult,
    onEntryLocationPrepare: () -> Unit
) {
    val scanSourceUiController = rememberNfcScanSourceUiController()

    LaunchedEffect(activity, scanMode) {
        if (activity != null) {
            when (scanMode) {
                ScanMode.SYSTEM_NFC -> scanSourceUiController.startSystemNfc(activity)
                ScanMode.EXTERNAL_RFID -> scanSourceUiController.startExternalReader(activity)
            }
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

    DisposableEffect(activity, scanMode) {
        onDispose {
            activity?.let { currentActivity ->
                when (scanMode) {
                    ScanMode.SYSTEM_NFC -> scanSourceUiController.stopSystemNfc(currentActivity)
                    ScanMode.EXTERNAL_RFID -> scanSourceUiController.stopExternalReader(currentActivity)
                }
            }
        }
    }

    LaunchedEffect(uiState, signInMode) {
        if (signInMode == SignInMode.END_ORDER && uiState is NfcSignInUiState.Success) {
            locationTrackingViewModel.onStopClicked()
        }
    }
}
