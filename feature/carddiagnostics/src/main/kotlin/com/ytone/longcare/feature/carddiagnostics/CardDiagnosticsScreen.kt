package com.ytone.longcare.feature.carddiagnostics

import android.content.ClipData
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class CardDiagnosticsMode { NFC, R65C }

/** Local-only UI; the host owns native NFC and the page lifecycle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDiagnosticsScreen(
    mode: CardDiagnosticsMode,
    active: Boolean,
    nfcSupported: Boolean,
    nfcEnabled: Boolean,
    nfcTag: String?,
    onModeChange: (CardDiagnosticsMode) -> Unit,
    onClearNfc: () -> Unit,
    onOpenNfcSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: R65CHidInputViewModel = hiltViewModel()
    val state by viewModel.panelState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = stringResource(R.string.nfc_validation_clipboard_label)
    val copied = stringResource(R.string.nfc_validation_copy_success)
    val copy: (String) -> Unit = { value ->
        scope.launch {
            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
        }
    }
    val capture = active && mode == CardDiagnosticsMode.R65C
    DisposableEffect(capture) {
        if (capture) viewModel.requestRefocus() else viewModel.onFieldFocusChanged(false)
        onDispose { viewModel.onFieldFocusChanged(false) }
    }
    Scaffold(topBar = {
        CenterAlignedTopAppBar(
            title = { Text(stringResource(R.string.nfc_validation_title)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.nfc_validation_back))
                }
            },
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CardDiagnosticsMode.entries.forEach { item ->
                    FilterChip(
                        selected = item == mode,
                        onClick = { onModeChange(item) },
                        label = { Text(item.name) },
                        modifier = Modifier.testTag("card_mode_${item.name}"),
                    )
                }
            }
            if (mode == CardDiagnosticsMode.NFC) {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(stringResource(R.string.nfc_validation_native_title),
                            style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(when {
                            !nfcSupported -> R.string.nfc_not_supported
                            !nfcEnabled -> R.string.nfc_validation_native_disabled
                            else -> R.string.nfc_validation_native_ready
                        }))
                        if (nfcSupported && !nfcEnabled) {
                            Button(onClick = onOpenNfcSettings) {
                                Text(stringResource(R.string.nfc_validation_open_settings))
                            }
                        }
                        ValidationValue(stringResource(R.string.nfc_validation_normalized_uid),
                            if (nfcTag != null && nfcTag.isBlank()) {
                                stringResource(R.string.nfc_validation_no_uid)
                            } else nfcTag.toDisplayValue(), "nfc_uid")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = onClearNfc) {
                                Text(stringResource(R.string.nfc_validation_clear))
                            }
                            Button(onClick = { nfcTag?.let(copy) }, enabled = !nfcTag.isNullOrBlank()) {
                                Text(stringResource(R.string.nfc_validation_copy))
                            }
                        }
                    }
                }
            } else {
                R65CHidInputValidationCard(state, viewModel::requestRefocus, viewModel::clearLastResult) {
                    state.lastNormalizedUid?.let(copy)
                    viewModel.requestRefocus()
                }
            }
        }
        if (capture) {
            R65CHidInputCapture(true, state.focusRequestToken,
                viewModel::onFieldFocusChanged, viewModel::onCapturedKey)
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
