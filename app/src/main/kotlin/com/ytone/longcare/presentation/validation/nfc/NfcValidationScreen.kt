package com.ytone.longcare.presentation.validation.nfc

import android.app.Activity
import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.showShortToast
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NfcValidationScreen(
    activity: Activity,
    nfcTestHelper: NfcTestHelper,
    onNavigateBack: () -> Unit,
    onOpenNfcSettings: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val supportsNfc = remember(context) { NfcUtils.isNfcSupported(context) }
    var nfcEnabled by remember(context) { mutableStateOf(NfcUtils.isNfcEnabled(context)) }
    val r65cViewModel: R65CHidInputViewModel = hiltViewModel()
    val r65cState by r65cViewModel.panelState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val copySuccessMessage = stringResource(R.string.nfc_validation_copy_success)

    DisposableEffect(activity, lifecycleOwner, nfcTestHelper, supportsNfc) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nfcEnabled = NfcUtils.isNfcEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (supportsNfc) nfcTestHelper.enable(activity)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            nfcTestHelper.disable(activity)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.nfc_validation_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.nfc_validation_back),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (supportsNfc) {
                    NativeNfcValidationCard(
                        nfcEnabled = nfcEnabled,
                        onOpenNfcSettings = onOpenNfcSettings,
                    )
                } else {
                    R65CHidInputValidationCard(
                        state = r65cState,
                        onRequestRefocus = r65cViewModel::requestRefocus,
                        onClearResult = r65cViewModel::clearLastResult,
                        onCopyResult = {
                            r65cState.lastNormalizedUid?.let { uid ->
                                coroutineScope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(ClipData.newPlainText("R65C UID", uid)),
                                    )
                                    context.showShortToast(copySuccessMessage)
                                }
                                r65cViewModel.requestRefocus()
                            }
                        },
                    )
                }
            }
        }

        if (!supportsNfc) {
            R65CHidInputCapture(
                enabled = true,
                focusRequestToken = r65cState.focusRequestToken,
                onFocusChanged = r65cViewModel::onFieldFocusChanged,
                onKeyCaptured = r65cViewModel::onCapturedKey,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }

    if (supportsNfc) nfcTestHelper.NfcTagDialog()
}

@Composable
private fun NativeNfcValidationCard(
    nfcEnabled: Boolean,
    onOpenNfcSettings: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nfc_validation_native_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text =
                    stringResource(
                        if (nfcEnabled) {
                            R.string.nfc_validation_native_ready
                        } else {
                            R.string.nfc_validation_native_disabled
                        },
                    ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!nfcEnabled) {
                Button(
                    onClick = onOpenNfcSettings,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.nfc_validation_open_settings))
                }
            }
        }
    }
}

@Composable
private fun R65CHidInputValidationCard(
    state: R65CHidPanelState,
    onRequestRefocus: () -> Unit,
    onClearResult: () -> Unit,
    onCopyResult: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.nfc_validation_r65c_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.nfc_validation_r65c_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            ValidationValue(
                label = stringResource(R.string.nfc_validation_status),
                value = captureStateLabel(state.captureState),
                testTag = "r65c_status_label",
            )
            ValidationValue(
                label = stringResource(R.string.nfc_validation_live_input),
                value = state.liveInputBuffer.toDisplayValue(),
                testTag = "r65c_live_input_value",
            )
            ValidationValue(
                label = stringResource(R.string.nfc_validation_raw_input),
                value = state.lastRawInput.toDisplayValue(),
                testTag = "r65c_last_raw_value",
            )
            ValidationValue(
                label = stringResource(R.string.nfc_validation_normalized_uid),
                value =
                    state.lastNormalizedUid
                        ?: stringResource(R.string.nfc_validation_no_uid),
                testTag = "r65c_last_uid_value",
            )
            ValidationValue(
                label = stringResource(R.string.nfc_validation_completed_at),
                value = state.lastCompletedAt.toDisplayValue(),
                testTag = "r65c_last_completed_at",
            )
            OutlinedButton(
                onClick = onRequestRefocus,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.nfc_validation_refocus))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onClearResult,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.nfc_validation_clear))
                }
                Button(
                    onClick = onCopyResult,
                    enabled = !state.lastNormalizedUid.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.nfc_validation_copy))
                }
            }
        }
    }
}

@Composable
private fun ValidationValue(
    label: String,
    value: String,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.testTag(testTag),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun captureStateLabel(state: R65CHidCaptureState): String =
    when (state) {
        R65CHidCaptureState.WaitingForFocus ->
            stringResource(R.string.nfc_validation_status_waiting_focus)
        R65CHidCaptureState.ReadyForScan ->
            stringResource(R.string.nfc_validation_status_ready)
        R65CHidCaptureState.ReceivingInput ->
            stringResource(R.string.nfc_validation_status_receiving)
        R65CHidCaptureState.LastCaptureSucceeded ->
            stringResource(R.string.nfc_validation_status_success)
        R65CHidCaptureState.LastCaptureFailed ->
            stringResource(
                R.string.nfc_validation_status_failure,
                stringResource(R.string.nfc_validation_parse_failure),
            )
    }

@Composable
private fun String?.toDisplayValue(): String =
    this
        ?.replace("\r", "\\r")
        ?.replace("\n", "\\n")
        ?.takeIf(String::isNotBlank)
        ?: stringResource(R.string.nfc_validation_empty_value)
