package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
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
    scanMode: ScanMode,
    nfcViewModel: NfcWorkflowViewModel,
    onLocationRequest: suspend () -> LocationRequestResult,
) {
    val scanSourceUiController = rememberNfcScanSourceUiController()
    val currentLocationRequest by rememberUpdatedState(onLocationRequest)

    LaunchedEffect(activity, scanMode) {
        if (activity != null) {
            when (scanMode) {
                ScanMode.SYSTEM_NFC -> scanSourceUiController.startSystemNfc(activity)
                ScanMode.EXTERNAL_RFID -> scanSourceUiController.startExternalReader(activity)
            }
            nfcViewModel.refreshExternalReaderReadyState()
        }
    }

    LaunchedEffect(activity, nfcViewModel, orderKey, signInMode, endOderInfo) {
        nfcViewModel.observeScanEvents(
            orderKey = orderKey,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            onLocationRequest = { currentLocationRequest() },
        )
    }

    DisposableEffect(activity, nfcViewModel) {
        onDispose {
            nfcViewModel.stopScanRequests(preserveWorkflow = shouldPreserveNfcWorkflow(activity))
        }
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

}

internal fun shouldPreserveNfcWorkflow(activity: Activity?): Boolean =
    activity?.isChangingConfigurations == true
