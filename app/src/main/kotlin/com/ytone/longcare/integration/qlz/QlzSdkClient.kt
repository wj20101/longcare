package com.ytone.longcare.integration.qlz

import android.app.Activity
import android.content.Context
import com.evenmed.sdk.call.CheckConfig
import com.evenmed.sdk.call.CheckIml
import com.evenmed.sdk.call.ErrorCodeConfig
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QlzSdkClient @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    @Volatile
    private var initializedSdkKey: String? = null
    private val sessionLeases = QlzSessionLeaseRegistry()

    @Synchronized
    fun initialize(): QlzSdkInitialization {
        val sdkKey = BuildConfig.QLZ_SDK_KEY.trim()
        if (sdkKey.isEmpty()) {
            return QlzSdkInitialization.MissingSdkKey
        }
        if (initializedSdkKey == sdkKey) {
            return QlzSdkInitialization.Ready
        }

        return try {
            val config =
                CheckConfig.Builder()
                    .setSdkKey(sdkKey)
                    .setHttpConfig(HTTP_TIMEOUT_SECONDS, HTTP_MAX_RETRY)
                    .setZiLiMode(false)
                    .setHorizontalMode(false)
                    .setBlueScanTime(BLUETOOTH_SCAN_TIMEOUT_MILLIS)
                    .setAutoConnectLastDevice(true)
                    .enableReConnect(false)
                    .setConnectDeviceOutTime(DEVICE_CONNECT_TIMEOUT_SECONDS)
                    .setCheckNullOutTime(CHECK_IDLE_TIMEOUT_SECONDS)
                    .setNeedCheckData(true)
                    .setAutoOpenRes(false)
                    .build()
            CheckIml.init(appContext, config)
            initializedSdkKey = sdkKey
            QlzSdkInitialization.Ready
        } catch (_: Throwable) {
            QlzSdkInitialization.Failed(
                appContext.getString(R.string.sales_error_evaluation_service_start),
            )
        }
    }

    fun getDeviceId(): Result<String> =
        runCatching {
            val initialization = initialize()
            check(initialization is QlzSdkInitialization.Ready) {
                initializationMessage(initialization)
            }
            CheckIml.getDeviceId(appContext).orEmpty().also {
                check(it.isNotBlank()) {
                    appContext.getString(
                        R.string.sales_error_evaluation_device_prepare_short
                    )
                }
            }
        }

    internal fun createEvaluationSession(
        activity: Activity,
        uploadContext: QlzEvaluationUploadContext,
        onEvent: (QlzSdkEvent) -> Unit,
        onStateChanged: (QlzEvaluationUiState) -> Unit,
    ): QlzEvaluationSessionCreation {
        if (activity.isFinishing || activity.isDestroyed) {
            return QlzEvaluationSessionCreation.Blocked(
                issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
                recoveryAction = QlzEvaluationRecoveryAction.EXIT,
            )
        }
        val initialization = initialize()
        if (initialization !is QlzSdkInitialization.Ready) {
            return QlzEvaluationSessionCreation.Blocked(
                issue = QlzEvaluationIssue.SDK_UNAVAILABLE,
                recoveryAction = QlzEvaluationRecoveryAction.EXIT,
            )
        }
        activity.qlzBluetoothEnvironmentIssue(requiredRuntimePermissions())?.let { issue ->
            return QlzEvaluationSessionCreation.Blocked(
                issue = issue,
                recoveryAction = issue.environmentRecoveryAction(),
            )
        }
        val leaseId =
            sessionLeases.acquire()
                ?: return QlzEvaluationSessionCreation.Blocked(
                    issue = QlzEvaluationIssue.SESSION_BUSY,
                    recoveryAction = QlzEvaluationRecoveryAction.EXIT,
                )
        return QlzEvaluationSessionCreation.Ready(
            QlzEvaluationSession(
                driverFactory =
                    QlzEvaluationDriverFactory {
                        QlzVendorEvaluationDriver(activity)
                    },
                uploadContext = uploadContext,
                onEvent = onEvent,
                onStateChanged = onStateChanged,
                releaseLease = { sessionLeases.release(leaseId) },
            )
        )
    }

    fun requiredRuntimePermissions(): Array<String> = qlzRequiredRuntimePermissions()

    private fun initializationMessage(
        initialization: QlzSdkInitialization,
    ): String =
        when (initialization) {
            QlzSdkInitialization.Ready ->
                appContext.getString(R.string.sales_evaluation_service_ready)

            QlzSdkInitialization.MissingSdkKey ->
                appContext.getString(
                    R.string.sales_error_evaluation_service_configuration
                )

            is QlzSdkInitialization.Failed -> initialization.message
        }

    private companion object {
        const val HTTP_TIMEOUT_SECONDS = 12
        const val HTTP_MAX_RETRY = 2
        const val BLUETOOTH_SCAN_TIMEOUT_MILLIS = 30_000L
        const val DEVICE_CONNECT_TIMEOUT_SECONDS = 15
        const val CHECK_IDLE_TIMEOUT_SECONDS = 180
    }
}

sealed interface QlzSdkInitialization {
    data object Ready : QlzSdkInitialization

    data object MissingSdkKey : QlzSdkInitialization

    data class Failed(
        val message: String,
    ) : QlzSdkInitialization
}

sealed interface QlzSdkEvent {
    data class Completed(
        val recordId: String,
    ) : QlzSdkEvent

    data object Cancelled : QlzSdkEvent

    data class Error(
        val code: Int,
        val message: String,
    ) : QlzSdkEvent {
        val requiresTokenRefresh: Boolean
            get() =
                code == ErrorCodeConfig.error_token_outtime ||
                    code == ErrorCodeConfig.error_no_token
    }
}
