package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R
import com.ytone.longcare.features.nfc.vm.NfcLoadingReason
import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.ui.components.BottomSafeActionContainer

@Composable
internal fun NfcWorkflowBottomBar(
    signInState: SignInState,
    signInMode: SignInMode,
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
    loadingReason: NfcLoadingReason?,
    onSuccessClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        BottomSafeActionContainer(
            horizontalPadding = 16.dp,
            topPadding = 16.dp,
            extraBottomPadding = 16.dp
        ) {
            when (signInState) {
                SignInState.SUCCESS -> {
                    val buttonText = when (signInMode) {
                        SignInMode.START_ORDER -> stringResource(R.string.common_next_step)
                        SignInMode.END_ORDER -> stringResource(R.string.nfc_sign_out_complete_service)
                    }
                    ActionButton(text = buttonText, onClick = onSuccessClick)
                }

                SignInState.FAILURE -> {
                    ActionButton(
                        text = stringResource(R.string.nfc_sign_in_retry),
                        onClick = onRetryClick
                    )
                }

                SignInState.IDLE -> {
                    val idleCopy = resolveNfcWorkflowIdleCopy(scanMode, readerUiState)
                    val hintRes = loadingReason?.let(::resolveLoadingCopyRes)
                        ?: resolveCopyRes(idleCopy.bottomHintKey)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color.White.copy(alpha = 0.9f)
                        )
                    ) {
                        Text(
                            text = stringResource(hintRes),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            textAlign = TextAlign.Center,
                            color = Color.Black.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}
