package com.ytone.longcare.features.nfctest.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class R65CHidInputTestViewModel @Inject constructor(
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var completionDelayMillis: Long = DEFAULT_COMPLETION_DELAY_MILLIS
    private var completionJob: Job? = null

    internal constructor(
        parser: ExternalRfidTagParser,
        nowProvider: () -> String,
        completionDelayMillis: Long,
    ) : this(parser) {
        this.nowProvider = nowProvider
        this.completionDelayMillis = completionDelayMillis
    }

    private val _panelState = MutableStateFlow(R65CHidPanelState())
    val panelState: StateFlow<R65CHidPanelState> = _panelState.asStateFlow()

    fun onFieldFocusChanged(isFocused: Boolean) {
        if (isFocused) {
            _panelState.update { state ->
                state.copy(
                    captureState = if (state.liveInputBuffer.isEmpty()) {
                        R65CHidCaptureState.ReadyForScan
                    } else {
                        R65CHidCaptureState.ReceivingInput
                    },
                )
            }
            return
        }

        cancelCompletionJob()
        _panelState.update { it.copy(captureState = R65CHidCaptureState.WaitingForFocus) }
    }

    fun onInputChanged(newValue: String) {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                liveInputBuffer = newValue,
                captureState = if (newValue.isEmpty()) {
                    R65CHidCaptureState.ReadyForScan
                } else {
                    R65CHidCaptureState.ReceivingInput
                },
            )
        }

        if (newValue.contains('\n') || newValue.contains('\r')) {
            finalizeCapture(newValue)
            return
        }

        if (newValue.isNotBlank()) {
            completionJob = viewModelScope.launch {
                delay(completionDelayMillis)
                val pendingBuffer = _panelState.value.liveInputBuffer
                if (pendingBuffer.isNotBlank()) {
                    completionJob = null
                    finalizeCapture(pendingBuffer)
                }
                completionJob = null
            }
        }
    }

    fun requestRefocus() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                captureState = if (it.liveInputBuffer.isEmpty()) {
                    R65CHidCaptureState.ReadyForScan
                } else {
                    R65CHidCaptureState.ReceivingInput
                },
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    fun clearLastResult() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                captureState = R65CHidCaptureState.ReadyForScan,
                liveInputBuffer = "",
                lastRawInput = null,
                lastNormalizedUid = null,
                lastCompletedAt = null,
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun finalizeCapture(rawInput: String) {
        val normalized = parser.normalize(rawInput)
        _panelState.update {
            it.copy(
                captureState = if (normalized == null) {
                    R65CHidCaptureState.LastCaptureFailed("未解析出卡号")
                } else {
                    R65CHidCaptureState.LastCaptureSucceeded
                },
                liveInputBuffer = "",
                lastRawInput = rawInput,
                lastNormalizedUid = normalized,
                lastCompletedAt = nowProvider(),
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun cancelCompletionJob() {
        completionJob?.cancel()
        completionJob = null
    }

    private fun nowString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onCleared() {
        cancelCompletionJob()
        super.onCleared()
    }

    private companion object {
        const val DEFAULT_COMPLETION_DELAY_MILLIS = 400L
    }
}
