package com.ytone.longcare.integration.qlz

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.evenmed.mode.CheckAssessPayment
import com.evenmed.mode.CheckPatient
import com.evenmed.mode.CheckRecordIdMode
import com.evenmed.sdk.call.CheckCallIml
import com.evenmed.sdk.call.CheckIml
import com.evenmed.sdk.call.ConnectDeviceHelp
import com.evenmed.sdk.call.ErrorCodeConfig
import com.falth.data.AssessedData
import com.falth.data.RecordInputData
import java.util.ArrayList

/** The only production type that translates mutable QLZ/Bluetooth callbacks into app state. */
internal class QlzVendorEvaluationDriver(
    activity: Activity,
) : QlzEvaluationDriver {
    private var activity: Activity? = activity
    private var listener: ((QlzEvaluationDriverEvent) -> Unit)? = null
    private var scanner: QlzVendorScanner? = null
    private var connector: ConnectDeviceHelp? = null
    private val deviceCatalog = QlzDeviceCatalog<BluetoothDevice>()
    private val uploadBuffer = QlzUploadBuffer<AssessedData, RecordInputData>()
    private var selectedDeviceId: String? = null
    private var closed = false

    override fun authorize(
        token: String,
        listener: (QlzEvaluationDriverEvent) -> Unit,
    ) {
        check(!closed)
        this.listener = listener
        val hostActivity = checkNotNull(activity)
        connector =
            ConnectDeviceHelp(
                hostActivity,
                uploadCallback,
                connectionCallback,
            )
        CheckIml.startCheck(
            hostActivity,
            token,
            object : CheckCallIml.CheckUserCallback {
                override fun onTokenOut() {
                    emit(QlzEvaluationDriverEvent.TokenExpired())
                }

                override fun onSuccess(data: CheckPatient?) {
                    emit(QlzEvaluationDriverEvent.Authorized)
                }

                override fun onError(code: Int, errorMessage: String?) {
                    emitAuthorizationFailure(code)
                }
            },
        )
    }

    override fun startScan() {
        check(!closed)
        val hostActivity = checkNotNull(activity)
        val activeScanner =
            scanner ?: QlzVendorScanner(
                hostActivity,
                onStarted = {
                    emit(QlzEvaluationDriverEvent.ScanStarted)
                },
                onStopped = {
                    emit(QlzEvaluationDriverEvent.ScanStopped)
                },
                onDevices = ::publishDeviceSnapshot,
            ).also { scanner = it }
        activeScanner.startScan(SCAN_TIMEOUT_MILLIS)
    }

    override fun stopScan() {
        scanner?.stopScan()
    }

    override fun connect(deviceId: String) {
        check(!closed)
        val device = deviceCatalog.resolve(deviceId)
        if (device == null) {
            emit(
                QlzEvaluationDriverEvent.Failed(
                    code = ErrorCodeConfig.error_check_device_info,
                    issue = QlzEvaluationIssue.DEVICE_INFO,
                    recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
                )
            )
            return
        }
        selectedDeviceId = deviceId
        checkNotNull(connector).connect(device)
    }

    override fun reconnect() {
        check(!closed)
        checkNotNull(connector).reConnect()
    }

    override fun upload(context: QlzEvaluationUploadContext) {
        if (closed) return
        val measurementSnapshot = uploadBuffer.beginInitialUpload()
        if (measurementSnapshot.isNullOrEmpty()) {
            emit(
                QlzEvaluationDriverEvent.UploadFailed(
                    ErrorCodeConfig.error_check_up_null
                )
            )
            return
        }
        try {
            checkNotNull(connector).sendData(
                measurementSnapshot,
                context.latitude,
                context.longitude,
                context.address,
                null,
            )
        } catch (_: Throwable) {
            uploadBuffer.recordFailure(null)
            emit(
                QlzEvaluationDriverEvent.Failed(
                    code = ErrorCodeConfig.error_check_up_null,
                    issue = QlzEvaluationIssue.UPLOAD_FAILED,
                    recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                )
            )
        }
    }

    override fun retryUpload() {
        if (closed) return
        val uploadSnapshot = uploadBuffer.beginRetry()
        if (uploadSnapshot == null) {
            emit(
                QlzEvaluationDriverEvent.UploadFailed(
                    ErrorCodeConfig.error_check_up_null
                )
            )
            return
        }
        try {
            checkNotNull(connector).sendData(uploadSnapshot)
        } catch (_: Throwable) {
            uploadBuffer.recordFailure(uploadSnapshot)
            emit(
                QlzEvaluationDriverEvent.UploadFailed(
                    ErrorCodeConfig.error_check_up_neterror
                )
            )
        }
    }

    override fun abortCheck() {
        connector?.breakCheck()
    }

    override fun close() {
        if (closed) return
        closed = true
        listener = null
        uploadBuffer.clear()
        deviceCatalog.clear()
        connector?.onDestroy()
        connector = null
        scanner?.close()
        scanner = null
        activity = null
    }

    private fun publishDeviceSnapshot(devices: ArrayList<BluetoothDevice>?) {
        if (closed) return
        val hostActivity = activity ?: return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                hostActivity,
                Manifest.permission.BLUETOOTH_CONNECT,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            emit(
                QlzEvaluationDriverEvent.Failed(
                    code = ErrorCodeConfig.code_othererror,
                    issue = QlzEvaluationIssue.PERMISSION_REQUIRED,
                    recoveryAction = QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT,
                )
            )
            return
        }
        // The SDK reuses its ArrayList. Copy it before reading or dispatching anything.
        val callbackSnapshot = ArrayList(devices.orEmpty())
        val scannedDevices =
            callbackSnapshot.mapNotNull { device ->
                val address = runCatching { device.address }.getOrNull().orEmpty()
                if (address.isBlank()) {
                    null
                } else {
                    QlzScannedDevice(
                        displayName = runCatching { device.name }.getOrNull().orEmpty(),
                        address = address,
                        payload = device,
                    )
                }
            }
        emit(
            QlzEvaluationDriverEvent.DevicesChanged(
                deviceCatalog.snapshot(scannedDevices)
            )
        )
    }

    private val uploadCallback =
        object : CheckCallIml.UpDataCallback {
            override fun onUpFail(
                data: RecordInputData?,
                errorCode: Int,
                errorMessage: String?,
            ) {
                if (closed) return
                uploadBuffer.recordFailure(data)
                if (data == null) {
                    emit(
                        QlzEvaluationDriverEvent.Failed(
                            code = errorCode,
                            issue = QlzEvaluationIssue.UPLOAD_FAILED,
                            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                        )
                    )
                } else {
                    emit(QlzEvaluationDriverEvent.UploadFailed(errorCode))
                }
            }

            override fun onUpSuccess(result: CheckRecordIdMode?) {
                if (closed) return
                uploadBuffer.complete()
                emit(
                    QlzEvaluationDriverEvent.UploadSucceeded(
                        recordId = result?.recordid.orEmpty(),
                        ignoredVendorReportUrl = result?.url.orEmpty(),
                        score = result?.score1.orEmpty(),
                    )
                )
            }
        }

    private val connectionCallback =
        object : CheckCallIml.ConnectDeviceCallback {
            override fun onConnectBegin(deviceName: String?) {
                emit(
                    QlzEvaluationDriverEvent.Connecting(
                        deviceName = deviceName.orEmpty().trim().take(MAX_DEVICE_NAME_LENGTH)
                    )
                )
            }

            override fun onConnectFail() {
                emit(
                    QlzEvaluationDriverEvent.Failed(
                        code = ErrorCodeConfig.code_othererror,
                        issue = QlzEvaluationIssue.CONNECTION_FAILED,
                        recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
                    )
                )
            }

            override fun onConnectLos() {
                emit(
                    QlzEvaluationDriverEvent.Failed(
                        code = ErrorCodeConfig.code_othererror,
                        issue = QlzEvaluationIssue.CONNECTION_LOST,
                        recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
                    )
                )
            }

            override fun onCheckOutTime() {
                emit(
                    QlzEvaluationDriverEvent.Failed(
                        code = ErrorCodeConfig.error_server_outtime,
                        issue = QlzEvaluationIssue.CHECK_TIMEOUT,
                        recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
                    )
                )
            }

            override fun onConnectSuccess(
                name: String?,
                address: String?,
                additionalMessage: String?,
            ) {
                emit(
                    QlzEvaluationDriverEvent.Connected(
                        deviceName = name.orEmpty().trim().take(MAX_DEVICE_NAME_LENGTH),
                        deviceId = selectedDeviceId,
                    )
                )
            }

            override fun onConnectError(code: Int, errorMessage: String?) {
                emitFailure(code)
            }

            override fun onCheckStart() {
                emit(QlzEvaluationDriverEvent.CheckStarted)
            }

            override fun onNeedPay(
                message: String?,
                payment: CheckAssessPayment?,
            ) {
                emit(QlzEvaluationDriverEvent.PaymentRequired)
                runCatching { connector?.breakCheck() }
            }

            override fun onCheckEnd(data: ArrayList<AssessedData>?) {
                if (closed) return
                val measurementSnapshot = ArrayList(data.orEmpty()).toList()
                if (measurementSnapshot.isEmpty()) {
                    emitFailure(ErrorCodeConfig.error_check_data_null)
                    return
                }
                if (uploadBuffer.acceptMeasurements(measurementSnapshot)) {
                    emit(QlzEvaluationDriverEvent.MeasurementCompleted)
                }
            }

            override fun onCheckState(fingers: BooleanArray?) {
                emit(
                    QlzEvaluationDriverEvent.FingerContactsChanged(
                        QlzFingerContacts.from(fingers?.copyOf() ?: booleanArrayOf())
                    )
                )
            }

            override fun onCheckPro(successCount: Int, allCount: Int) {
                emit(
                    QlzEvaluationDriverEvent.ProgressChanged(
                        successCount = successCount,
                        totalCount = allCount,
                    )
                )
            }

            override fun onPowerChange(powerIn: Boolean) {
                emit(QlzEvaluationDriverEvent.PowerChanged(powerIn))
            }
        }

    private fun emitFailure(code: Int) {
        val mapped = mapQlzFailure(code)
        if (mapped.issue == QlzEvaluationIssue.TOKEN_EXPIRED) {
            emit(QlzEvaluationDriverEvent.TokenExpired(code))
        } else {
            emit(
                QlzEvaluationDriverEvent.Failed(
                    code = code,
                    issue = mapped.issue,
                    recoveryAction = mapped.recoveryAction,
                )
            )
        }
    }

    private fun emitAuthorizationFailure(code: Int) {
        val mapped = mapQlzAuthorizationFailure(code)
        if (mapped.issue == QlzEvaluationIssue.TOKEN_EXPIRED) {
            emit(QlzEvaluationDriverEvent.TokenExpired(code))
        } else {
            emit(
                QlzEvaluationDriverEvent.Failed(
                    code = code,
                    issue = mapped.issue,
                    recoveryAction = mapped.recoveryAction,
                )
            )
        }
    }

    private fun emit(event: QlzEvaluationDriverEvent) {
        if (!closed) listener?.invoke(event)
    }

    private companion object {
        const val SCAN_TIMEOUT_MILLIS = 30_000L
        const val MAX_DEVICE_NAME_LENGTH = 48
    }
}
