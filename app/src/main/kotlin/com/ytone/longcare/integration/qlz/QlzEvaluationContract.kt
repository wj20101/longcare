package com.ytone.longcare.integration.qlz

import com.evenmed.sdk.call.ErrorCodeConfig
import java.util.Locale

/** App-owned representation of a QLZ device. The vendor address never leaves this boundary. */
data class QlzDeviceOption(
    val id: String,
    val displayName: String,
    val maskedIdentifier: String,
)

/** Finger order follows the vendor contract: little finger through thumb. */
data class QlzFingerContacts(
    val little: Boolean = false,
    val ring: Boolean = false,
    val middle: Boolean = false,
    val index: Boolean = false,
    val thumb: Boolean = false,
) {
    val allConnected: Boolean
        get() = little && ring && middle && index && thumb

    fun asList(): List<Boolean> = listOf(little, ring, middle, index, thumb)

    companion object {
        fun from(values: BooleanArray): QlzFingerContacts {
            val snapshot = values.copyOf()
            return QlzFingerContacts(
                little = snapshot.getOrElse(0) { false },
                ring = snapshot.getOrElse(1) { false },
                middle = snapshot.getOrElse(2) { false },
                index = snapshot.getOrElse(3) { false },
                thumb = snapshot.getOrElse(4) { false },
            )
        }
    }
}

/** Optional upload metadata. Empty values are intentional and are never guessed. */
data class QlzEvaluationUploadContext(
    val latitude: String = "",
    val longitude: String = "",
    val address: String = "",
) {
    fun normalized(): QlzEvaluationUploadContext =
        copy(
            latitude = latitude.trim(),
            longitude = longitude.trim(),
            address = address.trim(),
        )
}

enum class QlzEvaluationStage {
    IDLE,
    AUTHORIZING,
    READY_TO_SCAN,
    SCANNING,
    SCAN_RESULTS,
    SCAN_EMPTY,
    CONNECTING,
    CONNECTED,
    MEASURING,
    POWER_PAUSED,
    UPLOADING,
    COMPLETED,
    BLOCKED,
    ERROR,
    CLOSED,
}

enum class QlzEvaluationRecoveryAction {
    RETRY_AUTHORIZATION,
    RETRY_SCAN,
    RETRY_CONNECTION,
    RETRY_UPLOAD,
    RECHECK_ENVIRONMENT,
    EXIT,
}

enum class QlzEvaluationIssue {
    PERMISSION_REQUIRED,
    BLUETOOTH_UNSUPPORTED,
    BLUETOOTH_DISABLED,
    LOCATION_SERVICE_DISABLED,
    SDK_UNAVAILABLE,
    SESSION_BUSY,
    TOKEN_EXPIRED,
    USER_NOT_ELIGIBLE,
    NETWORK,
    CONNECTION_FAILED,
    CONNECTION_LOST,
    CHECK_TIMEOUT,
    NO_MEASUREMENT_DATA,
    DEVICE_UNAUTHORIZED,
    LOW_POWER,
    DEVICE_SERVICE,
    DEVICE_INFO,
    WEAK_SIGNAL,
    MEASUREMENT_ENDED,
    CHARGING,
    PAYMENT_REQUIRED,
    UPLOAD_FAILED,
    UNKNOWN,
}

data class QlzEvaluationUiState(
    val stage: QlzEvaluationStage = QlzEvaluationStage.IDLE,
    val devices: List<QlzDeviceOption> = emptyList(),
    val selectedDevice: QlzDeviceOption? = null,
    val fingerContacts: QlzFingerContacts = QlzFingerContacts(),
    val successCount: Int = 0,
    val totalCount: Int = 0,
    val powerConnected: Boolean = false,
    val issue: QlzEvaluationIssue? = null,
    val recoveryAction: QlzEvaluationRecoveryAction? = null,
    val preparationSeconds: Int? = null,
    val showMeasurementProgress: Boolean = false,
) {
    val progressPercent: Int
        get() = if (totalCount <= 0) 0 else
            (successCount.coerceIn(0, totalCount).toLong() * 100 / totalCount).toInt()

    val progressFraction: Float
        get() =
            if (totalCount <= 0) {
                0f
            } else {
                successCount.coerceIn(0, totalCount).toFloat() / totalCount
            }

    val isBusy: Boolean
        get() =
            stage in
                setOf(
                    QlzEvaluationStage.AUTHORIZING,
                    QlzEvaluationStage.SCANNING,
                    QlzEvaluationStage.CONNECTING,
                    QlzEvaluationStage.UPLOADING,
                )
}

internal sealed interface QlzEvaluationDriverEvent {
    data object Authorized : QlzEvaluationDriverEvent
    data object ScanStarted : QlzEvaluationDriverEvent
    data object ScanStopped : QlzEvaluationDriverEvent

    data class DevicesChanged(
        val devices: List<QlzDeviceOption>,
    ) : QlzEvaluationDriverEvent

    data class Connecting(
        val deviceName: String,
    ) : QlzEvaluationDriverEvent

    data class Connected(
        val deviceName: String,
        val deviceId: String?,
    ) : QlzEvaluationDriverEvent

    data object CheckStarted : QlzEvaluationDriverEvent

    data class FingerContactsChanged(
        val contacts: QlzFingerContacts,
    ) : QlzEvaluationDriverEvent

    data class ProgressChanged(
        val successCount: Int,
        val totalCount: Int,
    ) : QlzEvaluationDriverEvent

    data class PowerChanged(
        val connected: Boolean,
    ) : QlzEvaluationDriverEvent

    data object MeasurementCompleted : QlzEvaluationDriverEvent

    data class UploadFailed(
        val code: Int,
    ) : QlzEvaluationDriverEvent

    data class UploadSucceeded(
        val recordId: String,
    ) : QlzEvaluationDriverEvent

    data class Failed(
        val code: Int,
        val issue: QlzEvaluationIssue,
        val recoveryAction: QlzEvaluationRecoveryAction,
    ) : QlzEvaluationDriverEvent

    data class TokenExpired(
        val code: Int = ErrorCodeConfig.error_token_outtime,
    ) : QlzEvaluationDriverEvent

    data object PaymentRequired : QlzEvaluationDriverEvent
}

internal interface QlzEvaluationDriver {
    fun authorize(
        token: String,
        listener: (QlzEvaluationDriverEvent) -> Unit,
    )

    fun startScan()

    fun stopScan()

    fun connect(deviceId: String)

    fun reconnect()

    fun upload(context: QlzEvaluationUploadContext)

    fun retryUpload()

    fun abortCheck()

    fun close()
}

internal fun interface QlzEvaluationDriverFactory {
    fun create(): QlzEvaluationDriver
}

internal sealed interface QlzEvaluationSessionCreation {
    data class Ready(
        val session: QlzEvaluationSession,
    ) : QlzEvaluationSessionCreation

    data class Blocked(
        val issue: QlzEvaluationIssue,
        val recoveryAction: QlzEvaluationRecoveryAction,
    ) : QlzEvaluationSessionCreation
}

internal data class QlzScannedDevice<T>(
    val displayName: String,
    val address: String,
    val payload: T,
)

/** Creates stable opaque IDs while retaining raw Bluetooth data only inside the adapter. */
internal class QlzDeviceCatalog<T> {
    private val opaqueIdByAddress = linkedMapOf<String, String>()
    private val payloadByOpaqueId = linkedMapOf<String, T>()
    private var nextId = 1

    fun snapshot(devices: List<QlzScannedDevice<T>>): List<QlzDeviceOption> {
        val copiedDevices = devices.toList()
        val seenAddresses = mutableSetOf<String>()
        return copiedDevices.mapNotNull { device ->
            val normalizedAddress = device.address.trim().uppercase(Locale.ROOT)
            if (normalizedAddress.isEmpty() || !seenAddresses.add(normalizedAddress)) {
                return@mapNotNull null
            }
            val opaqueId =
                opaqueIdByAddress.getOrPut(normalizedAddress) {
                    "qlz-device-${nextId++}"
                }
            payloadByOpaqueId[opaqueId] = device.payload
            QlzDeviceOption(
                id = opaqueId,
                displayName = device.displayName.trim().take(MAX_DEVICE_NAME_LENGTH),
                maskedIdentifier = normalizedAddress.maskBluetoothIdentifier(),
            )
        }.toList()
    }

    fun resolve(opaqueId: String): T? = payloadByOpaqueId[opaqueId]

    fun clear() {
        opaqueIdByAddress.clear()
        payloadByOpaqueId.clear()
        nextId = 1
    }

    private companion object {
        const val MAX_DEVICE_NAME_LENGTH = 48
    }
}

/** Keeps one measurement payload and one failed upload payload in memory for this session only. */
internal class QlzUploadBuffer<Measurement, RetryPayload> {
    private var measurements: List<Measurement>? = null
    private var retryPayload: RetryPayload? = null
    private var uploadInFlight = false
    private var measurementAccepted = false

    fun acceptMeasurements(values: List<Measurement>): Boolean {
        if (measurementAccepted || values.isEmpty()) return false
        measurements = values.toList()
        measurementAccepted = true
        return true
    }

    fun beginInitialUpload(): List<Measurement>? {
        if (uploadInFlight) return null
        val snapshot = measurements?.toList() ?: return null
        uploadInFlight = true
        return snapshot
    }

    fun recordFailure(payload: RetryPayload?) {
        uploadInFlight = false
        retryPayload = payload
    }

    fun beginRetry(): RetryPayload? {
        if (uploadInFlight) return null
        val snapshot = retryPayload ?: return null
        uploadInFlight = true
        return snapshot
    }

    fun complete() {
        measurements = null
        retryPayload = null
        uploadInFlight = false
    }

    fun clear() {
        measurements = null
        retryPayload = null
        uploadInFlight = false
        measurementAccepted = false
    }

    internal fun hasRetryPayload(): Boolean = retryPayload != null
}

internal fun String.maskBluetoothIdentifier(): String {
    val parts = split(':')
    return if (parts.size == 6 && parts.all { it.length == 2 }) {
        "••:••:••:••:${parts[4]}:${parts[5]}"
    } else {
        "••••"
    }
}

internal data class QlzMappedFailure(
    val issue: QlzEvaluationIssue,
    val recoveryAction: QlzEvaluationRecoveryAction,
)

internal fun mapQlzFailure(code: Int): QlzMappedFailure =
    when (code) {
        ErrorCodeConfig.error_token_outtime,
        ErrorCodeConfig.error_no_token,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.TOKEN_EXPIRED,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_no_key -> QlzMappedFailure(
            issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_no_userinfo,
        ErrorCodeConfig.error_user_cancheck_fail,
        ErrorCodeConfig.code_user_id,
        ErrorCodeConfig.code_user_realname,
        ErrorCodeConfig.code_user_gender,
        ErrorCodeConfig.code_user_appid,
        ErrorCodeConfig.code_user_openid,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.USER_NOT_ELIGIBLE,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_network,
        ErrorCodeConfig.error_server_outtime,
        ErrorCodeConfig.error_server_fail,
        ErrorCodeConfig.error_server_gson,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.NETWORK,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        )

        ErrorCodeConfig.error_check_data_null -> QlzMappedFailure(
            issue = QlzEvaluationIssue.NO_MEASUREMENT_DATA,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_check_device_au -> QlzMappedFailure(
            issue = QlzEvaluationIssue.DEVICE_UNAUTHORIZED,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        )

        ErrorCodeConfig.error_check_device_power_low -> QlzMappedFailure(
            issue = QlzEvaluationIssue.LOW_POWER,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
        )

        ErrorCodeConfig.error_check_device_server -> QlzMappedFailure(
            issue = QlzEvaluationIssue.DEVICE_SERVICE,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
        )

        ErrorCodeConfig.error_check_device_info -> QlzMappedFailure(
            issue = QlzEvaluationIssue.DEVICE_INFO,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        )

        ErrorCodeConfig.error_check_device_sigle -> QlzMappedFailure(
            issue = QlzEvaluationIssue.WEAK_SIGNAL,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
        )

        ErrorCodeConfig.error_check_end -> QlzMappedFailure(
            issue = QlzEvaluationIssue.MEASUREMENT_ENDED,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_check_up_neterror,
        ErrorCodeConfig.error_check_up_now,
        ErrorCodeConfig.error_check_up_null,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.UPLOAD_FAILED,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_UPLOAD,
        )

        ErrorCodeConfig.code_finish_check,
        ErrorCodeConfig.code_finish_res,
        ErrorCodeConfig.code_check_cancel,
        ErrorCodeConfig.code_res_success,
        ErrorCodeConfig.code_check_state,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.UNKNOWN,
            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
        )

        ErrorCodeConfig.error_null,
        ErrorCodeConfig.code_othererror,
        -> QlzMappedFailure(
            issue = QlzEvaluationIssue.UNKNOWN,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        )

        else -> QlzMappedFailure(
            issue = QlzEvaluationIssue.UNKNOWN,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        )
    }

internal fun mapQlzAuthorizationFailure(code: Int): QlzMappedFailure {
    val mapped = mapQlzFailure(code)
    return if (
        mapped.issue == QlzEvaluationIssue.TOKEN_EXPIRED ||
        mapped.issue == QlzEvaluationIssue.SDK_UNAVAILABLE ||
        mapped.issue == QlzEvaluationIssue.USER_NOT_ELIGIBLE
    ) {
        mapped
    } else {
        mapped.copy(recoveryAction = QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION)
    }
}
