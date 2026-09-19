package com.ytone.longcare.integration.qlz

internal object QlzEvaluationReducer {
    fun reduce(
        state: QlzEvaluationUiState,
        event: QlzEvaluationDriverEvent,
    ): QlzEvaluationUiState =
        when (event) {
            QlzEvaluationDriverEvent.Authorized ->
                if (state.stage == QlzEvaluationStage.AUTHORIZING) {
                    state.copy(
                        stage = QlzEvaluationStage.READY_TO_SCAN,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            QlzEvaluationDriverEvent.ScanStarted ->
                if (
                    state.stage in
                    setOf(
                        QlzEvaluationStage.READY_TO_SCAN,
                        QlzEvaluationStage.SCAN_EMPTY,
                        QlzEvaluationStage.SCAN_RESULTS,
                        QlzEvaluationStage.ERROR,
                    )
                ) {
                    state.copy(
                        stage = QlzEvaluationStage.SCANNING,
                        devices = emptyList(),
                        selectedDevice = null,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.DevicesChanged ->
                if (
                    state.stage == QlzEvaluationStage.SCANNING ||
                    state.stage == QlzEvaluationStage.SCAN_RESULTS
                ) {
                    val devices = event.devices.toList()
                    state.copy(
                        stage =
                            if (devices.isEmpty()) {
                                QlzEvaluationStage.SCANNING
                            } else {
                                QlzEvaluationStage.SCAN_RESULTS
                            },
                        devices = devices,
                    )
                } else {
                    state
                }

            QlzEvaluationDriverEvent.ScanStopped ->
                if (
                    state.stage == QlzEvaluationStage.SCANNING ||
                    state.stage == QlzEvaluationStage.SCAN_RESULTS
                ) {
                    state.copy(
                        stage =
                            if (state.devices.isEmpty()) {
                                QlzEvaluationStage.SCAN_EMPTY
                            } else {
                                QlzEvaluationStage.SCAN_RESULTS
                            },
                        recoveryAction =
                            if (state.devices.isEmpty()) {
                                QlzEvaluationRecoveryAction.RETRY_SCAN
                            } else {
                                null
                            },
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.Connecting ->
                if (
                    state.stage in
                    setOf(
                        QlzEvaluationStage.SCANNING,
                        QlzEvaluationStage.SCAN_RESULTS,
                        QlzEvaluationStage.ERROR,
                    )
                ) {
                    state.copy(
                        stage = QlzEvaluationStage.CONNECTING,
                        selectedDevice =
                            state.selectedDevice
                                ?: state.devices.firstOrNull {
                                    it.displayName == event.deviceName
                                },
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.Connected ->
                if (state.stage == QlzEvaluationStage.CONNECTING) {
                    state.copy(
                        stage = QlzEvaluationStage.CONNECTED,
                        selectedDevice =
                            event.deviceId?.let { id ->
                                state.devices.firstOrNull { it.id == id }
                            } ?: state.selectedDevice,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            QlzEvaluationDriverEvent.CheckStarted ->
                if (
                    state.stage == QlzEvaluationStage.CONNECTING ||
                    state.stage == QlzEvaluationStage.CONNECTED ||
                    state.stage == QlzEvaluationStage.POWER_PAUSED
                ) {
                    state.copy(
                        stage = QlzEvaluationStage.MEASURING,
                        powerConnected = false,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.FingerContactsChanged ->
                if (
                    state.stage == QlzEvaluationStage.CONNECTED ||
                    state.stage == QlzEvaluationStage.MEASURING ||
                    state.stage == QlzEvaluationStage.POWER_PAUSED
                ) {
                    state.copy(fingerContacts = event.contacts)
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.ProgressChanged ->
                if (
                    state.stage == QlzEvaluationStage.CONNECTED ||
                    state.stage == QlzEvaluationStage.MEASURING ||
                    state.stage == QlzEvaluationStage.POWER_PAUSED
                ) {
                    val total = event.totalCount.coerceAtLeast(0)
                    state.copy(
                        stage =
                            if (state.powerConnected) {
                                QlzEvaluationStage.POWER_PAUSED
                            } else {
                                QlzEvaluationStage.MEASURING
                            },
                        successCount = event.successCount.coerceIn(0, total),
                        totalCount = total,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.PowerChanged ->
                if (
                    state.stage == QlzEvaluationStage.CONNECTED ||
                    state.stage == QlzEvaluationStage.MEASURING ||
                    state.stage == QlzEvaluationStage.POWER_PAUSED
                ) {
                    state.copy(
                        stage =
                            if (event.connected) {
                                QlzEvaluationStage.POWER_PAUSED
                            } else {
                                QlzEvaluationStage.MEASURING
                            },
                        powerConnected = event.connected,
                        issue =
                            if (event.connected) {
                                QlzEvaluationIssue.CHARGING
                            } else {
                                null
                            },
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            QlzEvaluationDriverEvent.MeasurementCompleted ->
                if (
                    state.stage == QlzEvaluationStage.MEASURING ||
                    state.stage == QlzEvaluationStage.POWER_PAUSED ||
                    state.stage == QlzEvaluationStage.CONNECTED
                ) {
                    state.copy(
                        stage = QlzEvaluationStage.UPLOADING,
                        powerConnected = false,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.UploadFailed ->
                if (state.stage == QlzEvaluationStage.UPLOADING) {
                    state.copy(
                        stage = QlzEvaluationStage.ERROR,
                        issue = QlzEvaluationIssue.UPLOAD_FAILED,
                        recoveryAction = QlzEvaluationRecoveryAction.RETRY_UPLOAD,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.UploadSucceeded ->
                if (state.stage == QlzEvaluationStage.UPLOADING) {
                    state.copy(
                        stage = QlzEvaluationStage.COMPLETED,
                        successCount = state.totalCount,
                        issue = null,
                        recoveryAction = null,
                    )
                } else {
                    state
                }

            is QlzEvaluationDriverEvent.Failed ->
                if (
                    state.stage == QlzEvaluationStage.CLOSED ||
                    state.stage == QlzEvaluationStage.COMPLETED ||
                    state.stage == QlzEvaluationStage.BLOCKED
                ) {
                    state
                } else {
                    state.copy(
                        stage = QlzEvaluationStage.ERROR,
                        issue = event.issue,
                        recoveryAction = event.recoveryAction,
                    )
                }

            is QlzEvaluationDriverEvent.TokenExpired ->
                if (
                    state.stage == QlzEvaluationStage.CLOSED ||
                    state.stage == QlzEvaluationStage.COMPLETED ||
                    state.stage == QlzEvaluationStage.BLOCKED
                ) {
                    state
                } else {
                    state.copy(
                        stage = QlzEvaluationStage.ERROR,
                        issue = QlzEvaluationIssue.TOKEN_EXPIRED,
                        recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                    )
                }

            QlzEvaluationDriverEvent.PaymentRequired ->
                if (
                    state.stage == QlzEvaluationStage.CLOSED ||
                    state.stage == QlzEvaluationStage.COMPLETED
                ) {
                    state
                } else {
                    state.copy(
                        stage = QlzEvaluationStage.BLOCKED,
                        issue = QlzEvaluationIssue.PAYMENT_REQUIRED,
                        recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                    )
                }
        }
}
