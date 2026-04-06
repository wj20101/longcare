package com.ytone.longcare.features.nfctest.vm

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.ytone.longcare.common.utils.ExternalRfidTagParser
import com.ytone.longcare.common.utils.UsbHostProbeManager
import com.ytone.longcare.common.utils.UsbHostProbeResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@HiltViewModel
class TypeCRfidTestViewModel @Inject constructor(
    private val probeManager: UsbHostProbeManager,
    private val parser: ExternalRfidTagParser,
) : ViewModel() {

    private val _panelState = MutableStateFlow(TypeCRfidPanelState())
    val panelState: StateFlow<TypeCRfidPanelState> = _panelState.asStateFlow()

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
                lastUpdatedAt = nowString(),
            )

            is UsbHostProbeResult.ReadFailure -> TypeCRfidPanelState(
                probeState = UsbProbeUiState.ReadFailed(result.message),
                deviceSummary = result.summary,
                lastUpdatedAt = nowString(),
            )

            is UsbHostProbeResult.ReadSuccess -> {
                val text = result.payload.toString(Charsets.UTF_8)
                TypeCRfidPanelState(
                    probeState = UsbProbeUiState.Ready,
                    deviceSummary = result.summary,
                    rawPayload = result.payload,
                    rawPayloadText = text,
                    parsedTagId = parser.normalize(text),
                    lastUpdatedAt = nowString(),
                )
            }
        }
    }

    private fun nowString(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
}
