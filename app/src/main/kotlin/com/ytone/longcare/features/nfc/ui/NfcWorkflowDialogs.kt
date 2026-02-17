package com.ytone.longcare.features.nfc.ui

import androidx.compose.runtime.Composable
import com.ytone.longcare.features.nfc.vm.EndOrderParams
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel

@Composable
internal fun NfcWorkflowDialogs(
    pendingNfcData: NfcWorkflowViewModel.PendingNfcData?,
    uiState: NfcSignInUiState,
    onConfirmLocationActivation: (NfcWorkflowViewModel.PendingNfcData) -> Unit,
    onCancelLocationActivation: () -> Unit,
    onConfirmEndOrder: (EndOrderParams) -> Unit,
    onCancelEndOrder: () -> Unit
) {
    pendingNfcData?.let { data ->
        LocationActivationDialog(
            onConfirm = { onConfirmLocationActivation(data) },
            onCancel = onCancelLocationActivation
        )
    }

    when (uiState) {
        is NfcSignInUiState.ShowConfirmDialog -> {
            EndOrderConfirmDialog(
                message = uiState.message,
                onConfirm = { onConfirmEndOrder(uiState.endOrderParams) },
                onCancel = onCancelEndOrder
            )
        }

        else -> Unit
    }
}
