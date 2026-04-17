package com.ytone.longcare.features.nfctest.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.debug.NfcTestEntrySession
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.nfctest.vm.NfcTestViewModel
import com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModel
import com.ytone.longcare.features.nfctest.vm.R65CHidRawValidationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTestScreen(
    actions: NfcTestActions,
) {
    val nfcTestViewModel: NfcTestViewModel = hiltViewModel()
    val r65cViewModel: R65CHidInputTestViewModel = hiltViewModel()
    val rawValidationViewModel: R65CHidRawValidationViewModel = hiltViewModel()
    val r65cPanelState by r65cViewModel.panelState.collectAsStateWithLifecycle()
    val rawValidationState by rawValidationViewModel.panelState.collectAsStateWithLifecycle()
    val testEntryEnabled by NfcTestEntrySession.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val nfcTestHelper = if (testEntryEnabled) {
        nfcTestViewModel.getHelper()
    } else {
        null
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    if (testEntryEnabled && nfcTestHelper != null) {
        BindNfcTestLifecycle(
            enabled = true,
            context = context,
            lifecycleOwner = lifecycleOwner,
            onEnable = nfcTestViewModel::enableNfcTest,
            onDisable = nfcTestViewModel::disableNfcTest,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { keyEvent ->
                toR65CHidCapturedKeyEventIfRelevant(
                    isListening = rawValidationState.isListening,
                    currentState = rawValidationState.captureState,
                    keyEvent = keyEvent.nativeKeyEvent,
                )?.let(rawValidationViewModel::onHostCapturedKey)
                false
            },
    ) {
        Scaffold(
            topBar = { NfcTestTopBar(onNavigateBack = actions.onNavigateBack) },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            NfcTestBody(
                enabled = testEntryEnabled,
                r65cPanelState = r65cPanelState,
                rawValidationState = rawValidationState,
                onR65CInputChanged = r65cViewModel::onInputChanged,
                onR65CFocusChanged = r65cViewModel::onFieldFocusChanged,
                onR65CRequestRefocus = r65cViewModel::requestRefocus,
                onR65CClearResult = r65cViewModel::clearLastResult,
                onRawTextFieldValueChanged = rawValidationViewModel::onTextFieldValueChanged,
                onRawFocusChanged = {},
                onRawStartListening = rawValidationViewModel::startListening,
                onRawStopListening = rawValidationViewModel::stopListening,
                onRawRequestRefocus = rawValidationViewModel::requestRefocus,
                onRawClearSession = rawValidationViewModel::clearLastSession,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    if (testEntryEnabled && nfcTestHelper != null) {
        nfcTestHelper.NfcTagDialog()
    }
}
