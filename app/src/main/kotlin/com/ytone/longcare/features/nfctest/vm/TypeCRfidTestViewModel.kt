package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostDeviceEvent
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class TypeCRfidTestViewModel @Inject constructor(
    private val probeManager: UsbHostProbeManager,
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private var nowProvider: () -> String = { nowString() }
    private var observeJob: Job? = null

    internal constructor(
        probeManager: UsbHostProbeManager,
        parser: ExternalRfidTagParser,
        nowProvider: () -> String,
    ) : this(probeManager, parser) {
        this.nowProvider = nowProvider
    }

    private val _panelState = MutableStateFlow(TypeCRfidPanelState())
    val panelState: StateFlow<TypeCRfidPanelState> = _panelState.asStateFlow()

    fun startObserving() {
        if (observeJob != null) return

        observeJob = viewModelScope.launch {
            probeManager.observeDeviceChanges().collect { event ->
                when (event) {
                    UsbHostDeviceEvent.Attached,
                    UsbHostDeviceEvent.Detached,
                    -> refreshDevices()
                }
            }
        }
    }

    fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
    }

    fun refreshDevices() {
        applyProbeResult(probeManager.refresh())
    }

    fun requestPermission(activity: Activity) {
        applyProbeResult(probeManager.requestPermission(activity))
    }

    fun attemptRead(activity: Activity) {
        _panelState.value = _panelState.value.copy(probeState = UsbProbeUiState.Reading)
        applyProbeResult(probeManager.attemptRead(activity))
    }

    private fun applyProbeResult(result: UsbHostProbeResult) {
        _panelState.value = when (result) {
            UsbHostProbeResult.NoDevice -> TypeCRfidPanelState(
                probeState = UsbProbeUiState.NoDevice,
            )

            is UsbHostProbeResult.DeviceFound -> TypeCRfidPanelState(
                probeState = if (result.hasPermission) {
                    UsbProbeUiState.Ready
                } else {
                    UsbProbeUiState.DeviceDetected
                },
                deviceSummary = result.summary,
                lastUpdatedAt = nowProvider(),
            )

            is UsbHostProbeResult.ReadFailure -> TypeCRfidPanelState(
                probeState = UsbProbeUiState.ReadFailed(result.message),
                deviceSummary = result.summary,
                lastUpdatedAt = nowProvider(),
            )

            is UsbHostProbeResult.ReadSuccess -> {
                val text = result.payload.toString(Charsets.UTF_8)
                TypeCRfidPanelState(
                    probeState = UsbProbeUiState.Ready,
                    deviceSummary = result.summary,
                    rawPayload = result.payload,
                    rawPayloadText = text,
                    parsedTagId = parser.normalize(text),
                    lastUpdatedAt = nowProvider(),
                )
            }
        }
    }

    private fun nowString(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

    override fun onCleared() {
        stopObserving()
        super.onCleared()
    }
}
