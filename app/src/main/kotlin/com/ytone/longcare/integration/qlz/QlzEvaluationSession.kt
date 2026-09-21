package com.ytone.longcare.integration.qlz

import com.evenmed.sdk.call.ErrorCodeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Ensures the process owns at most one active vendor measurement connection. */
internal class QlzSessionLeaseRegistry {
    private var activeLeaseId: Long? = null
    private var nextLeaseId = 1L

    @Synchronized
    fun acquire(): Long? {
        if (activeLeaseId != null) return null
        return nextLeaseId++.also { activeLeaseId = it }
    }

    @Synchronized
    fun release(leaseId: Long) {
        if (activeLeaseId == leaseId) {
            activeLeaseId = null
        }
    }

    @Synchronized
    fun hasActiveLease(): Boolean = activeLeaseId != null
}

/**
 * UI-scoped state machine for one QLZ evaluation.
 *
 * Vendor objects remain in [QlzEvaluationDriver]. Every callback is tagged with a generation so
 * callbacks from a replaced or closed driver cannot mutate the current state.
 */
internal class QlzEvaluationSession(
    private val driverFactory: QlzEvaluationDriverFactory,
    uploadContext: QlzEvaluationUploadContext,
    private val onEvent: (QlzSdkEvent) -> Unit,
    private val onStateChanged: (QlzEvaluationUiState) -> Unit = {},
    private val releaseLease: () -> Unit,
    private val presentationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    private val gripPreparation = QlzGripPreparation()
    private var preparationJob: Job? = null
    private val normalizedUploadContext = uploadContext.normalized()
    private val mutableState = MutableStateFlow(QlzEvaluationUiState())
    val state: StateFlow<QlzEvaluationUiState> = mutableState.asStateFlow()

    private var driver: QlzEvaluationDriver? = null
    private var generation = 0L
    private var closed = false
    private var foreground = true
    private var uploadRequested = false
    private var reauthorizingUpload = false

    @Synchronized
    fun authorize(token: String): Boolean {
        val current = mutableState.value
        if (closed || !foreground || token.isBlank()) return false
        if (current.stage != QlzEvaluationStage.IDLE &&
            !(current.stage == QlzEvaluationStage.ERROR &&
                (current.issue == QlzEvaluationIssue.TOKEN_EXPIRED ||
                    current.recoveryAction == QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION))
        ) return false
        if (uploadRequested) {
            reauthorizeUpload(token)
        } else {
            releaseCurrentDriver()
            startNewGeneration(token)
        }
        return true
    }

    @Synchronized
    fun startScan() {
        if (closed || !foreground) return
        val current = mutableState.value
        if (
            current.stage !in
            setOf(
                QlzEvaluationStage.READY_TO_SCAN,
                QlzEvaluationStage.SCAN_EMPTY,
                QlzEvaluationStage.SCAN_RESULTS,
                QlzEvaluationStage.ERROR,
            ) ||
            (
                current.stage == QlzEvaluationStage.ERROR &&
                    current.recoveryAction != QlzEvaluationRecoveryAction.RETRY_SCAN
            )
        ) {
            return
        }
        runCatching { driver?.stopScan() }
        applyDriverEvent(QlzEvaluationDriverEvent.ScanStarted)
        invokeDriverOrFail(
            issue = QlzEvaluationIssue.UNKNOWN,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_SCAN,
        ) { it.startScan() }
    }

    @Synchronized
    fun selectDevice(deviceId: String) {
        if (closed) return
        val current = mutableState.value
        if (
            current.stage != QlzEvaluationStage.SCANNING &&
            current.stage != QlzEvaluationStage.SCAN_RESULTS
        ) {
            return
        }
        val selected = current.devices.firstOrNull { it.id == deviceId } ?: return
        updateState(
            current.copy(
                stage = QlzEvaluationStage.CONNECTING,
                selectedDevice = selected,
                issue = null,
                recoveryAction = null,
            )
        )
        runCatching { driver?.stopScan() }
        invokeDriverOrFail(
            issue = QlzEvaluationIssue.CONNECTION_FAILED,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
        ) { it.connect(deviceId) }
    }

    @Synchronized
    fun retryConnection() {
        val current = mutableState.value
        if (
            closed ||
            current.stage != QlzEvaluationStage.ERROR ||
            current.recoveryAction != QlzEvaluationRecoveryAction.RETRY_CONNECTION
        ) {
            return
        }
        updateState(
            current.copy(
                stage = QlzEvaluationStage.CONNECTING,
                issue = null,
                recoveryAction = null,
            )
        )
        invokeDriverOrFail(
            issue = QlzEvaluationIssue.CONNECTION_FAILED,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_CONNECTION,
        ) { it.reconnect() }
    }

    private fun reauthorizeUpload(token: String) {
        val current = mutableState.value
        generation += 1
        val callbackGeneration = generation
        reauthorizingUpload = true
        updateState(current.copy(stage = QlzEvaluationStage.UPLOADING, issue = null, recoveryAction = null))
        invokeDriverOrFail(QlzEvaluationIssue.UPLOAD_FAILED, QlzEvaluationRecoveryAction.EXIT) {
            it.authorize(token.trim()) { event -> handleDriverEvent(callbackGeneration, event) }
        }
    }

    @Synchronized
    fun retryUpload() {
        val current = mutableState.value
        if (
            closed ||
            current.stage != QlzEvaluationStage.ERROR ||
            current.recoveryAction != QlzEvaluationRecoveryAction.RETRY_UPLOAD
        ) {
            return
        }
        updateState(
            current.copy(
                stage = QlzEvaluationStage.UPLOADING,
                issue = null,
                recoveryAction = null,
            )
        )
        invokeDriverOrFail(
            issue = QlzEvaluationIssue.UPLOAD_FAILED,
            recoveryAction = QlzEvaluationRecoveryAction.RETRY_UPLOAD,
        ) { it.retryUpload() }
    }

    @Synchronized
    fun onHostStarted() {
        if (closed) return
        foreground = true
        updateState(mutableState.value)
        if (mutableState.value.stage == QlzEvaluationStage.READY_TO_SCAN) {
            startScan()
        }
    }

    @Synchronized
    fun onHostStopped() {
        if (closed) return
        foreground = false
        updateState(mutableState.value.copy(fingerContacts = QlzFingerContacts()))
        if (
            mutableState.value.stage == QlzEvaluationStage.SCANNING ||
            mutableState.value.stage == QlzEvaluationStage.SCAN_RESULTS
        ) {
            runCatching { driver?.stopScan() }
            applyDriverEvent(QlzEvaluationDriverEvent.ScanStopped)
        }
    }

    fun cancel() {
        val shouldNotify = synchronized(this) { !closed }
        if (shouldNotify) onEvent(QlzSdkEvent.Cancelled)
        close()
    }

    override fun close() {
        val currentDriver: QlzEvaluationDriver?
        synchronized(this) {
            if (closed) return
            currentDriver = driver
            generation += 1
            closed = true
            preparationJob?.cancel()
            presentationScope.cancel()
            driver = null
            updateState(
                mutableState.value.copy(
                    stage = QlzEvaluationStage.CLOSED,
                    devices = emptyList(),
                    recoveryAction = null,
                )
            )
        }
        try {
            runCatching { currentDriver?.stopScan() }
            runCatching { currentDriver?.abortCheck() }
            runCatching { currentDriver?.close() }
        } finally {
            releaseLease()
        }
    }

    private fun startNewGeneration(token: String) {
        generation += 1
        uploadRequested = false
        reauthorizingUpload = false
        updateState(QlzEvaluationUiState(stage = QlzEvaluationStage.AUTHORIZING))
        val newDriver =
            runCatching { driverFactory.create() }
                .getOrElse {
                    handleDriverEvent(
                        generation,
                        QlzEvaluationDriverEvent.Failed(
                            code = ErrorCodeConfig.code_othererror,
                            issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
                            recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                        ),
                    )
                    return
                }
        driver = newDriver
        val callbackGeneration = generation
        runCatching {
            newDriver.authorize(token.trim()) { event ->
                handleDriverEvent(callbackGeneration, event)
            }
        }.onFailure {
            handleDriverEvent(
                callbackGeneration,
                QlzEvaluationDriverEvent.Failed(
                    code = ErrorCodeConfig.code_othererror,
                    issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
                    recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                ),
            )
        }
    }

    private fun handleDriverEvent(
        callbackGeneration: Long,
        event: QlzEvaluationDriverEvent,
    ) {
        synchronized(this) {
            if (closed || callbackGeneration != generation) return
            when (event) {
                QlzEvaluationDriverEvent.Authorized -> {
                    if (reauthorizingUpload && mutableState.value.stage == QlzEvaluationStage.UPLOADING) {
                        reauthorizingUpload = false
                        invokeDriverOrFail(QlzEvaluationIssue.UPLOAD_FAILED, QlzEvaluationRecoveryAction.RETRY_UPLOAD) {
                            it.retryUpload()
                        }
                    } else if (!uploadRequested && mutableState.value.stage == QlzEvaluationStage.AUTHORIZING) {
                        applyDriverEvent(event)
                        if (foreground) startScan()
                    }
                }

                is QlzEvaluationDriverEvent.UploadFailed -> {
                    if (mutableState.value.stage != QlzEvaluationStage.UPLOADING) return
                    if (event.code == 401 || event.code == 2001 ||
                        event.code == ErrorCodeConfig.error_token_outtime || event.code == ErrorCodeConfig.error_no_token
                    ) {
                        handleDriverEvent(callbackGeneration, QlzEvaluationDriverEvent.TokenExpired())
                    } else {
                        applyDriverEvent(event)
                    }
                }

                QlzEvaluationDriverEvent.MeasurementCompleted -> {
                    if (uploadRequested) return
                    val previousStage = mutableState.value.stage
                    applyDriverEvent(event)
                    if (
                        previousStage != mutableState.value.stage &&
                        mutableState.value.stage == QlzEvaluationStage.UPLOADING
                    ) {
                        uploadRequested = true
                        invokeDriverOrFail(
                            issue = QlzEvaluationIssue.UPLOAD_FAILED,
                            recoveryAction = QlzEvaluationRecoveryAction.RETRY_UPLOAD,
                        ) { it.upload(normalizedUploadContext) }
                    }
                }

                is QlzEvaluationDriverEvent.UploadSucceeded -> {
                    if (reauthorizingUpload || mutableState.value.stage != QlzEvaluationStage.UPLOADING) return
                    applyDriverEvent(event)
                    onEvent(
                        QlzSdkEvent.Completed(
                            recordId = event.recordId,
                        )
                    )
                }

                is QlzEvaluationDriverEvent.TokenExpired -> {
                    val previousState = mutableState.value
                    applyDriverEvent(event)
                    if (
                        mutableState.value != previousState &&
                        mutableState.value.issue == QlzEvaluationIssue.TOKEN_EXPIRED
                    ) {
                        reauthorizingUpload = false
                        onEvent(QlzSdkEvent.Error(code = event.code, message = ""))
                    }
                }

                is QlzEvaluationDriverEvent.Failed -> {
                    applyDriverEvent(if (uploadRequested) event.copy(recoveryAction = QlzEvaluationRecoveryAction.EXIT) else event)
                    reauthorizingUpload = false
                }

                else -> applyDriverEvent(event)
            }
        }
    }

    private fun applyDriverEvent(event: QlzEvaluationDriverEvent) {
        updateState(QlzEvaluationReducer.reduce(mutableState.value, event))
    }

    private fun updateState(newState: QlzEvaluationUiState) {
        if (newState.stage == QlzEvaluationStage.AUTHORIZING ||
            newState.stage == QlzEvaluationStage.CONNECTING
        ) gripPreparation.reset()
        val presented = gripPreparation.update(newState, foreground && !closed, nowMillis())
        if (presented != mutableState.value) {
            mutableState.value = presented
            onStateChanged(presented)
        }
        if (presented.preparationSeconds != null && preparationJob?.isActive != true) {
            preparationJob = presentationScope.launch {
                while (true) {
                    delay(100)
                    synchronized(this@QlzEvaluationSession) {
                        if (!closed) updateState(mutableState.value)
                    }
                }
            }
        } else if (presented.preparationSeconds == null) {
            preparationJob?.cancel()
            preparationJob = null
        }
    }

    private fun invokeDriverOrFail(
        issue: QlzEvaluationIssue,
        recoveryAction: QlzEvaluationRecoveryAction,
        block: (QlzEvaluationDriver) -> Unit,
    ) {
        val activeDriver = driver
        if (activeDriver == null) {
            applyDriverEvent(
                QlzEvaluationDriverEvent.Failed(
                    code = ErrorCodeConfig.code_othererror,
                    issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
                    recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                )
            )
            return
        }
        runCatching { block(activeDriver) }
            .onFailure {
                applyDriverEvent(
                    QlzEvaluationDriverEvent.Failed(
                        code = ErrorCodeConfig.code_othererror,
                        issue = issue,
                        recoveryAction = recoveryAction,
                    )
                )
            }
    }

    private fun releaseCurrentDriver() {
        val currentDriver = driver
        if (currentDriver != null) {
            generation += 1
            driver = null
            runCatching { currentDriver.stopScan() }
            runCatching { currentDriver.abortCheck() }
            runCatching { currentDriver.close() }
        }
        uploadRequested = false
    }
}
