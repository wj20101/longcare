package com.ytone.longcare.features.nfctest.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
class R65CHidRawValidationViewModel @Inject constructor() : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var completionDelayMillis: Long = DEFAULT_COMPLETION_DELAY_MILLIS
    private var completionJob: Job? = null

    internal constructor(
        nowProvider: () -> String,
        completionDelayMillis: Long,
    ) : this() {
        this.nowProvider = nowProvider
        this.completionDelayMillis = completionDelayMillis
    }

    private val _panelState = MutableStateFlow(R65CHidRawValidationState())
    val panelState: StateFlow<R65CHidRawValidationState> = _panelState.asStateFlow()

    fun onFocusChanged(isFocused: Boolean) {
        if (isFocused) {
            _panelState.update { state ->
                state.copy(
                    captureState = if (state.currentSessionEvents.isEmpty()) {
                        R65CHidRawCaptureState.ReadyForScan
                    } else {
                        R65CHidRawCaptureState.ReceivingKeys
                    },
                )
            }
            return
        }

        cancelCompletionJob()
        _panelState.update { it.copy(captureState = R65CHidRawCaptureState.WaitingForFocus) }
    }

    fun onTextFieldValueChanged(newValue: String) {
        _panelState.update { it.copy(textFieldValue = newValue) }
    }

    fun onCapturedKey(event: R65CHidCapturedKeyEvent) {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                captureState = R65CHidRawCaptureState.ReceivingKeys,
                currentSessionEvents = state.currentSessionEvents + event,
                currentSessionAssembledChars = state.currentSessionAssembledChars + event.visibleDisplayChar(),
            )
        }

        if (event.displayChar == "\\n") {
            completeSession(R65CHidCompletionReason.EnterKey)
            return
        }

        completionJob = viewModelScope.launch {
            delay(completionDelayMillis)
            if (_panelState.value.currentSessionEvents.isNotEmpty()) {
                completeSession(R65CHidCompletionReason.IdleTimeout)
            }
        }
    }

    fun requestRefocus() {
        _panelState.update {
            it.copy(focusRequestToken = it.focusRequestToken + 1)
        }
    }

    fun clearLastSession() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                captureState = R65CHidRawCaptureState.ReadyForScan,
                textFieldValue = "",
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = null,
                lastSessionAssembledChars = null,
                lastSessionEvents = emptyList(),
                lastCompletedReason = null,
                candidateValues = emptyList(),
                lastCompletedAt = null,
                focusRequestToken = it.focusRequestToken + 1,
            )
        }
    }

    private fun completeSession(reason: R65CHidCompletionReason) {
        cancelCompletionJob()

        _panelState.update { state ->
            val sessionEvents = state.currentSessionEvents
            val assembledChars = state.currentSessionAssembledChars
            val textFieldValue = state.textFieldValue

            state.copy(
                captureState = R65CHidRawCaptureState.Completed,
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = textFieldValue,
                lastSessionAssembledChars = assembledChars,
                lastSessionEvents = sessionEvents,
                lastCompletedReason = reason,
                candidateValues = buildR65CHidCandidateValues(textFieldValue, assembledChars),
                lastCompletedAt = nowProvider(),
                focusRequestToken = state.focusRequestToken + 1,
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

    private fun R65CHidCapturedKeyEvent.visibleDisplayChar(): String {
        return if (displayChar == "\\n") "" else displayChar
    }

    private companion object {
        const val DEFAULT_COMPLETION_DELAY_MILLIS = 400L
    }
}
