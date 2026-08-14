package com.ytone.longcare.presentation.validation.nfc

import android.view.KeyEvent
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
internal class R65CHidInputViewModel @Inject constructor(
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private var nowProvider: () -> String = ::nowString
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
                    captureState =
                        if (state.liveInputBuffer.isEmpty()) {
                            R65CHidCaptureState.ReadyForScan
                        } else {
                            R65CHidCaptureState.ReceivingInput
                        },
                )
            }
        } else {
            cancelCompletionJob()
            _panelState.update {
                it.copy(
                    captureState = R65CHidCaptureState.WaitingForFocus,
                    liveInputBuffer = "",
                )
            }
        }
    }

    fun onCapturedKey(event: R65CHidCapturedKeyEvent) {
        val nextValue =
            when {
                event.isTerminator() -> panelState.value.liveInputBuffer + "\n"
                event.unicodeChar == 0 -> panelState.value.liveInputBuffer
                else -> panelState.value.liveInputBuffer + event.unicodeChar.toChar()
            }
        onInputChanged(nextValue)
    }

    fun requestRefocus() {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                captureState = R65CHidCaptureState.ReadyForScan,
                liveInputBuffer = "",
                focusRequestToken = state.focusRequestToken + 1,
            )
        }
    }

    fun clearLastResult() {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                captureState = R65CHidCaptureState.ReadyForScan,
                liveInputBuffer = "",
                lastRawInput = null,
                lastNormalizedUid = null,
                lastCompletedAt = null,
                focusRequestToken = state.focusRequestToken + 1,
            )
        }
    }

    private fun onInputChanged(newValue: String) {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                liveInputBuffer = newValue,
                captureState =
                    if (newValue.isEmpty()) {
                        R65CHidCaptureState.ReadyForScan
                    } else {
                        R65CHidCaptureState.ReceivingInput
                    },
            )
        }

        if ('\n' in newValue || '\r' in newValue) {
            finalizeCapture(newValue)
        } else if (newValue.isNotBlank()) {
            completionJob =
                viewModelScope.launch {
                    delay(completionDelayMillis)
                    val pendingBuffer = _panelState.value.liveInputBuffer
                    if (pendingBuffer.isNotBlank()) {
                        finalizeCapture(pendingBuffer)
                    }
                    completionJob = null
                }
        }
    }

    private fun finalizeCapture(rawInput: String) {
        val normalizedUid = parser.normalize(rawInput)
        _panelState.update { state ->
            state.copy(
                captureState =
                    if (normalizedUid == null) {
                        R65CHidCaptureState.LastCaptureFailed
                    } else {
                        R65CHidCaptureState.LastCaptureSucceeded
                    },
                liveInputBuffer = "",
                lastRawInput = rawInput,
                lastNormalizedUid = normalizedUid,
                lastCompletedAt = nowProvider(),
                focusRequestToken = state.focusRequestToken + 1,
            )
        }
    }

    private fun R65CHidCapturedKeyEvent.isTerminator(): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            unicodeChar == '\n'.code ||
            unicodeChar == '\r'.code

    private fun cancelCompletionJob() {
        completionJob?.cancel()
        completionJob = null
    }

    private fun nowString(): String =
        SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onCleared() {
        cancelCompletionJob()
    }

    private companion object {
        const val DEFAULT_COMPLETION_DELAY_MILLIS = 400L
    }
}
