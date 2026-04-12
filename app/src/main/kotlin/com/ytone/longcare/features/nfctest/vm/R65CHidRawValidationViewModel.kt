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
    private var refocusRequestToken: Int = 0

    internal constructor(
        nowProvider: () -> String,
        completionDelayMillis: Long,
    ) : this() {
        this.nowProvider = nowProvider
        this.completionDelayMillis = completionDelayMillis
    }

    private val _panelState = MutableStateFlow(R65CHidRawValidationState())
    val panelState: StateFlow<R65CHidRawValidationState> = _panelState.asStateFlow()

    fun startListening() {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                isListening = true,
                captureState = if (state.currentSessionEvents.isEmpty()) {
                    R65CHidRawCaptureState.Armed
                } else {
                    R65CHidRawCaptureState.Capturing
                },
            )
        }
    }

    fun stopListening() {
        cancelCompletionJob()
        _panelState.update {
            it.copy(
                isListening = false,
                captureState = R65CHidRawCaptureState.Idle,
            )
        }
    }

    fun onTextFieldValueChanged(newValue: String) {
        _panelState.update { it.copy(textFieldValue = newValue) }
    }

    fun onHostCapturedKey(event: R65CHidCapturedKeyEvent) {
        val state = _panelState.value
        if (!state.isListening || !event.shouldAffectCurrentSession()) {
            return
        }

        cancelCompletionJob()
        _panelState.update { currentState ->
            currentState.copy(
                captureState = R65CHidRawCaptureState.Capturing,
                currentSessionEvents = currentState.currentSessionEvents + event,
                currentSessionAssembledChars = currentState.currentSessionAssembledChars + event.visibleDisplayChar(),
            )
        }

        if (event.isEnterKey()) {
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
        refocusRequestToken += 1
    }

    fun clearLastSession() {
        cancelCompletionJob()
        _panelState.update { state ->
            state.copy(
                captureState = if (state.isListening) {
                    R65CHidRawCaptureState.Armed
                } else {
                    R65CHidRawCaptureState.Idle
                },
                textFieldValue = "",
                currentSessionEvents = emptyList(),
                currentSessionAssembledChars = "",
                lastSessionTextFieldValue = null,
                lastSessionAssembledChars = null,
                lastSessionEvents = emptyList(),
                lastCompletedReason = null,
                candidateValues = emptyList(),
                lastCompletedAt = null,
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
                isListening = true,
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

    private fun R65CHidCapturedKeyEvent.shouldAffectCurrentSession(): Boolean {
        return action == KEY_ACTION_DOWN
    }

    private fun R65CHidCapturedKeyEvent.visibleDisplayChar(): String {
        return if (isEnterKey()) "" else displayChar
    }

    private fun R65CHidCapturedKeyEvent.isEnterKey(): Boolean {
        return displayChar == "\\n" || unicodeChar == '\n'.code || keyCode == KEY_CODE_ENTER
    }

    private companion object {
        const val DEFAULT_COMPLETION_DELAY_MILLIS = 400L
        const val KEY_ACTION_DOWN = 0
        const val KEY_CODE_ENTER = 66
    }
}
