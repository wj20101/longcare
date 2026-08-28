package com.ytone.longcare.integration.qlz

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import com.evenmed.mode.CheckRecordIdMode
import com.evenmed.sdk.call.ErrorCodeConfig
import com.google.gson.Gson
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QlzSdkClient internal constructor(
    private val appContext: Context,
    runtimeConfig: QlzSdkRuntimeConfig,
    private val vendorApi: QlzVendorApi,
) {
    @Inject
    constructor(
        @ApplicationContext appContext: Context,
    ) : this(
        appContext = appContext,
        runtimeConfig =
            QlzSdkRuntimeConfig(
                sdkKey = BuildConfig.QLZ_SDK_KEY,
                testMode = BuildConfig.QLZ_TEST_MODE,
            ),
        vendorApi = AndroidQlzVendorApi,
    )

    private val runtimeConfig = runtimeConfig.copy(sdkKey = runtimeConfig.sdkKey.trim())

    @Volatile
    private var initializedRuntimeConfig: QlzSdkRuntimeConfig? = null

    val isTestMode: Boolean
        get() = runtimeConfig.testMode

    @Synchronized
    fun initialize(): QlzSdkInitialization {
        if (runtimeConfig.sdkKey.isEmpty()) {
            return QlzSdkInitialization.MissingSdkKey
        }
        if (initializedRuntimeConfig == runtimeConfig) {
            return QlzSdkInitialization.Ready
        }

        return try {
            vendorApi.setTestMode(runtimeConfig.testMode)
            vendorApi.initialize(
                appContext,
                QlzVendorInitializationConfig(
                    sdkKey = runtimeConfig.sdkKey,
                    httpTimeoutSeconds = HTTP_TIMEOUT_SECONDS,
                    httpMaxRetry = HTTP_MAX_RETRY,
                    horizontalMode = false,
                    bluetoothScanTimeoutMillis = BLUETOOTH_SCAN_TIMEOUT_MILLIS,
                    autoConnectLastDevice = true,
                    deviceConnectTimeoutSeconds = DEVICE_CONNECT_TIMEOUT_SECONDS,
                    checkIdleTimeoutSeconds = CHECK_IDLE_TIMEOUT_SECONDS,
                    autoOpenResult = false,
                ),
            )
            initializedRuntimeConfig = runtimeConfig
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
            vendorApi.getDeviceId(appContext).orEmpty().also {
                check(it.isNotBlank()) {
                    appContext.getString(
                        R.string.sales_error_evaluation_device_prepare_short
                    )
                }
            }
        }

    /**
     * Returns the Bluetooth device currently held by the SDK.
     *
     * The vendor SDK does not expose a standalone connection listener. Its public
     * connection signal is the remembered/active Bluetooth device name, so the
     * production UI refreshes this value when the SDK page is entered or closed.
     */
    fun getConnectedDeviceName(): String? =
        runCatching {
            val initialization = initialize()
            check(initialization is QlzSdkInitialization.Ready) {
                initializationMessage(initialization)
            }
            vendorApi.getConnectedDeviceName()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }.getOrNull()

    fun openByToken(
        activity: Activity,
        token: String,
        onEvent: (QlzSdkEvent) -> Unit,
    ) {
        if (token.isBlank()) {
            onEvent(
                QlzSdkEvent.Error(
                    ErrorCodeConfig.error_no_token,
                    appContext.getString(
                        R.string.sales_error_evaluation_expired_short
                    ),
                )
            )
            return
        }
        val initialization = initialize()
        if (initialization !is QlzSdkInitialization.Ready) {
            onEvent(
                QlzSdkEvent.Error(
                    ErrorCodeConfig.error_no_key,
                    initializationMessage(initialization),
                )
            )
            return
        }

        try {
            vendorApi.openByToken(activity, token) { result ->
                onEvent(result.toQlzSdkEvent())
            }
        } catch (throwable: Throwable) {
            onEvent(
                QlzSdkEvent.Error(
                    ErrorCodeConfig.code_othererror,
                    throwable.message.toUserFacingEvaluationError(
                        fallbackMessage =
                            appContext.getString(
                                R.string.sales_error_evaluation_continue
                            )
                    ),
                )
            )
        }
    }

    fun requiredRuntimePermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun QlzVendorResult.toQlzSdkEvent(): QlzSdkEvent =
        when (errorCode) {
            ErrorCodeConfig.code_res_success -> parseCompletedEvent(data)
            ErrorCodeConfig.code_check_state ->
                QlzSdkEvent.Progress(
                    successCount = successCount,
                    totalCount = totalCount,
                )

            ErrorCodeConfig.code_finish_check -> QlzSdkEvent.DetectionPageClosed
            ErrorCodeConfig.code_finish_res -> QlzSdkEvent.ReportPageClosed
            ErrorCodeConfig.code_check_cancel -> QlzSdkEvent.Cancelled
            else ->
                QlzSdkEvent.Error(
                    code = errorCode,
                    message =
                        (
                            errorMessage?.takeIf { it.isNotBlank() }
                                ?: ErrorCodeConfig.getErrMsg(errorCode)
                        ).toUserFacingEvaluationError(
                            fallbackMessage =
                                appContext.getString(
                                    R.string.sales_error_evaluation_continue
                                )
                        ),
                )
        }

    private fun parseCompletedEvent(rawData: String?): QlzSdkEvent =
        try {
            val record = Gson().fromJson(rawData, CheckRecordIdMode::class.java)
            QlzSdkEvent.Completed(
                recordId = record?.recordid.orEmpty(),
                reportUrl = record?.url.orEmpty(),
                score = record?.score1.orEmpty(),
            )
        } catch (_: Throwable) {
            QlzSdkEvent.Error(
                code = ErrorCodeConfig.error_server_gson,
                message =
                    appContext.getString(R.string.sales_error_evaluation_result_read),
            )
        }

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

internal data class QlzSdkRuntimeConfig(
    val sdkKey: String,
    val testMode: Boolean,
)

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
        val reportUrl: String,
        val score: String,
    ) : QlzSdkEvent

    data class Progress(
        val successCount: Int,
        val totalCount: Int,
    ) : QlzSdkEvent

    data object DetectionPageClosed : QlzSdkEvent
    data object ReportPageClosed : QlzSdkEvent
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

private val DEVELOPMENT_COPY_PATTERN =
    Regex(
        pattern =
            """(?i)\b(?:sdk|token|api|url|http|sdkid|qlz)\b|""" +
                """设备\s*ID|错误码|code\s*[:=]|local\.properties""",
    )

internal fun String?.toUserFacingEvaluationError(
    fallbackMessage: String,
): String {
    val candidate = this?.trim().orEmpty()
    return candidate
        .takeIf {
            it.isNotBlank() &&
                !DEVELOPMENT_COPY_PATTERN.containsMatchIn(it)
        }
        ?: fallbackMessage
}
