package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import java.util.UUID
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
    val scanSessionId = remember(activity, nfcViewModel, orderKey, signInMode, endOderInfo, scanMode) {
        UUID.randomUUID().toString()
    }
    DisposableEffect(activity, nfcViewModel, orderKey, signInMode, endOderInfo, scanMode) {
        if (activity != null) {
            // Subscribe before enabling dispatch; a tag always belongs to this page attachment.
            nfcViewModel.observeScanEvents(
                orderKey = orderKey,
                signInMode = signInMode,
                endOderInfo = endOderInfo,
                scanSessionId = scanSessionId,
                onLocationRequest = { currentLocationRequest() },
            )
            when (scanMode) {
                ScanMode.SYSTEM_NFC -> scanSourceUiController.startSystemNfc(activity, scanSessionId)
                ScanMode.EXTERNAL_RFID -> scanSourceUiController.startExternalReader(activity)
            }
            nfcViewModel.refreshExternalReaderReadyState()
        }
        onDispose {
            activity?.let { currentActivity ->
                when (scanMode) {
                    ScanMode.SYSTEM_NFC -> scanSourceUiController.stopSystemNfc(currentActivity, scanSessionId)
                    ScanMode.EXTERNAL_RFID -> scanSourceUiController.stopExternalReader(currentActivity)
                }
            }
            nfcViewModel.stopScanRequests(
                preserveWorkflow = shouldPreserveNfcWorkflow(activity), scanSessionId = scanSessionId,
            )
        }
    }

}

internal fun shouldPreserveNfcWorkflow(activity: Activity?): Boolean =
    activity?.isChangingConfigurations == true
