package com.ytone.longcare.platform.sales

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.integration.qlz.QlzDeviceOption
import com.ytone.longcare.integration.qlz.QlzEvaluationIssue
import com.ytone.longcare.integration.qlz.QlzEvaluationRecoveryAction
import com.ytone.longcare.integration.qlz.QlzEvaluationSession
import com.ytone.longcare.integration.qlz.QlzEvaluationSessionCreation
import com.ytone.longcare.integration.qlz.QlzEvaluationStage
import com.ytone.longcare.integration.qlz.QlzEvaluationUiState
import com.ytone.longcare.integration.qlz.QlzEvaluationUploadContext
import com.ytone.longcare.integration.qlz.QlzSdkClient
import com.ytone.longcare.integration.qlz.QlzSdkEvent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI-scoped boundary for QLZ operations that require the current Activity. */
internal class SalesSdkUiController(
    private val qlzSdkClient: QlzSdkClient,
) : AutoCloseable {
    private val mutableUiState = MutableStateFlow(QlzEvaluationUiState())
    val uiState: StateFlow<QlzEvaluationUiState> = mutableUiState.asStateFlow()
    private var activeSession: QlzEvaluationSession? = null
    private var foreground = true

    fun requiredRuntimePermissions(): Array<String> = qlzSdkClient.requiredRuntimePermissions()

    /** Checks the host/environment before requesting a one-use credential. */
    fun prepareEvaluation(
        activity: Activity,
        uploadContext: QlzEvaluationUploadContext,
        onEvent: (QlzSdkEvent) -> Unit,
    ): Boolean {
        if (!foreground || activity.isFinishing || activity.isDestroyed) return false
        val existingSession = activeSession
        if (existingSession != null && uiState.value.stage != QlzEvaluationStage.CLOSED) {
            if (uiState.value.recoveryAction == QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT) {
                close()
            } else if (uiState.value.stage == QlzEvaluationStage.IDLE ||
                uiState.value.recoveryAction == QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION
            ) {
                return true
            } else {
                existingSession.startScan()
                return false
            }
        }
        when (
            val creation =
                qlzSdkClient.createEvaluationSession(
                    activity = activity,
                    uploadContext = uploadContext,
                    onEvent = onEvent,
                    onStateChanged = { mutableUiState.value = it },
                )
        ) {
            is QlzEvaluationSessionCreation.Ready -> {
                activeSession = creation.session
                mutableUiState.value = creation.session.state.value
                return true
            }

            is QlzEvaluationSessionCreation.Blocked -> {
                mutableUiState.value =
                    QlzEvaluationUiState(
                        stage = QlzEvaluationStage.BLOCKED,
                        issue = creation.issue,
                        recoveryAction = creation.recoveryAction,
                    )
            }
        }
        return false
    }

    fun authorizeEvaluation(token: String): Boolean =
        foreground && activeSession?.authorize(token) == true

    fun selectDevice(device: QlzDeviceOption) {
        activeSession?.selectDevice(device.id)
    }

    fun retryCurrentStep() {
        when (uiState.value.recoveryAction) {
            QlzEvaluationRecoveryAction.RETRY_SCAN -> activeSession?.startScan()
            QlzEvaluationRecoveryAction.RETRY_CONNECTION -> activeSession?.retryConnection()
            QlzEvaluationRecoveryAction.RETRY_UPLOAD -> activeSession?.retryUpload()
            QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT,
            QlzEvaluationRecoveryAction.RETRY_AUTHORIZATION,
            QlzEvaluationRecoveryAction.EXIT,
            null,
            -> Unit
        }
    }

    fun showPermissionRequired() {
        if (activeSession != null) return
        mutableUiState.value =
            QlzEvaluationUiState(
                stage = QlzEvaluationStage.BLOCKED,
                issue = QlzEvaluationIssue.PERMISSION_REQUIRED,
                recoveryAction = QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT,
            )
    }

    fun onHostStarted() {
        foreground = true
        activeSession?.onHostStarted()
    }

    fun onHostStopped() {
        foreground = false
        activeSession?.onHostStopped()
    }

    fun cancel() {
        activeSession?.cancel()
        activeSession = null
    }

    override fun close() {
        activeSession?.close()
        activeSession = null
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface SalesSdkUiEntryPoint {
    fun qlzSdkClient(): QlzSdkClient
}

@Composable
internal fun rememberSalesSdkUiController(): SalesSdkUiController {
    val applicationContext = LocalContext.current.applicationContext
    return remember(applicationContext) {
        SalesSdkUiController(applicationContext.qlzSdkClient())
    }
}

private fun Context.qlzSdkClient(): QlzSdkClient = EntryPointAccessors.fromApplication(
    this,
    SalesSdkUiEntryPoint::class.java,
).qlzSdkClient()
