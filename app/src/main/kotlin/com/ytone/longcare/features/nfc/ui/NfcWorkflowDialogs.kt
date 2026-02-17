package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.nfc.vm.EndOrderParams
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.features.nfc.vm.PendingNfcData

@Composable
internal fun NfcWorkflowDialogs(
    pendingNfcData: PendingNfcData?,
    uiState: NfcSignInUiState,
    onConfirmLocationActivation: (PendingNfcData) -> Unit,
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

@Composable
internal fun LocationActivationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "激活定位",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "定位未激活，需激活方可操作，激活后不可改",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "确定激活",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(text = "取消", fontSize = 14.sp)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
internal fun EndOrderConfirmDialog(
    message: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "确认结束工单",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A86FF)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(text = "确认", color = Color.White, fontSize = 14.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(text = "取消", fontSize = 14.sp)
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(12.dp)
    )
}
