package com.ytone.longcare.features.nfc.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.nfc.vm.NfcLoadingReason
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.features.nfc.vm.ReaderUiState
import com.ytone.longcare.features.nfc.vm.ScanMode
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NfcWorkflowTopBar(
    titleRes: Int,
    onBack: () -> Unit
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                stringResource(titleRes),
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(onClick = singleClick { onBack() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Color.White
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = Color.White,
            navigationIconContentColor = Color.White
        )
    )
}

@Composable
internal fun NfcWorkflowBodyContent(
    paddingValues: PaddingValues,
    orderKey: OrderKey,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo?,
    nfcViewModel: NfcWorkflowViewModel,
    signInState: SignInState,
    loadingReason: NfcLoadingReason?,
    scanMode: ScanMode,
    readerUiState: ReaderUiState,
) {
    val idleCopy = resolveNfcWorkflowIdleCopy(scanMode, readerUiState)
    val promptRes = when {
        loadingReason != null -> resolveLoadingCopyRes(loadingReason)
        signInState == SignInState.IDLE -> resolveCopyRes(idleCopy.promptKey)
        else -> R.string.nfc_sign_in_prompt
    }
    val statusOverrideRes = if (
        loadingReason != null
    ) {
        resolveLoadingCopyRes(loadingReason)
    } else if (
        signInState == SignInState.IDLE &&
        scanMode == ScanMode.EXTERNAL_RFID
    ) {
        resolveCopyRes(idleCopy.statusKey)
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stringResource(promptRes),
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(32.dp))

        SignInContentCard(
            signInState = signInState,
            statusOverrideRes = statusOverrideRes,
            showReadingIndicator = loadingReason != null || scanMode == ScanMode.EXTERNAL_RFID &&
                readerUiState == ReaderUiState.Reading,
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (scanMode == ScanMode.EXTERNAL_RFID) {
            R65cWorkflowHidCaptureSurface(
                readerUiState = readerUiState,
                onKeyCaptured = nfcViewModel::onR65cFallbackKeyEvent,
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        NfcWorkflowDebugMockButton(
            orderKey = orderKey,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            nfcViewModel = nfcViewModel
        )
    }
}
