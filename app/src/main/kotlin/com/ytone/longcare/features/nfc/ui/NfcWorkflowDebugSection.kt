package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

internal enum class SignInState {
    IDLE,
    SUCCESS,
    FAILURE
}

@Composable
internal fun NfcWorkflowDebugMockButton(
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    nfcViewModel: NfcWorkflowViewModel
) {
    if (!BuildConfig.USE_MOCK_DATA) return

    Button(
        onClick = {
            nfcViewModel.mockNfcScan(
                orderKey = orderKey,
                signInMode = signInMode,
                endOderInfo = endOderInfo
            )
        },
        colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Mock NFC Scan (Debug Only)")
    }
    Spacer(modifier = Modifier.height(24.dp))
}
