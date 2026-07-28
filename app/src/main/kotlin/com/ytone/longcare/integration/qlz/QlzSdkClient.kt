package com.ytone.longcare.integration.qlz

import android.Manifest
import android.app.Activity
import android.content.Context
import android.os.Build
import com.evenmed.mode.CheckRecordIdMode
import com.evenmed.sdk.call.CheckConfig
import com.evenmed.sdk.call.CheckIml
import com.evenmed.sdk.call.CheckResult
import com.evenmed.sdk.call.CheckStateData
import com.evenmed.sdk.call.ErrorCodeConfig
import com.evenmed.sdk.call.SDKCall
import com.google.gson.Gson
import com.ytone.longcare.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QlzSdkClient @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
) {
    @Volatile
    private var initializedSdkKey: String? = null

    val isTestMode: Boolean
        get() = BuildConfig.QLZ_TEST_MODE

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
            CheckIml.setTestMode(isTestMode)
            val config =
                CheckConfig.Builder()
                    .setSdkKey(sdkKey)
                    .setHttpConfig(HTTP_TIMEOUT_SECONDS, HTTP_MAX_RETRY)
                    .setHorizontalMode(false)
                    .setBlueScanTime(BLUETOOTH_SCAN_TIMEOUT_MILLIS)
                    .setAutoConnectLastDevice(true)
                    .setConnectDeviceOutTime(DEVICE_CONNECT_TIMEOUT_SECONDS)
                    .setCheckNullOutTime(CHECK_IDLE_TIMEOUT_SECONDS)
                    .setAutoOpenRes(false)
                    .build()
            CheckIml.init(appContext, config)
            initializedSdkKey = sdkKey
            QlzSdkInitialization.Ready
        } catch (_: Throwable) {
            QlzSdkInitialization.Failed(
                "评估服务启动失败，请稍后重试",
            )
        }
    }

    fun getDeviceId(): Result<String> =
        runCatching {
            val initialization = initialize()
            check(initialization is QlzSdkInitialization.Ready) {
                initialization.message
            }
            CheckIml.getDeviceId(appContext).orEmpty().also {
                check(it.isNotBlank()) { "评估设备准备失败" }
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
                initialization.message
            }
            CheckIml.getBlueDeviceName()
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
                    "本次评估已失效，请重新进入",
                )
            )
            return
        }
        val initialization = initialize()
        if (initialization !is QlzSdkInitialization.Ready) {
            onEvent(QlzSdkEvent.Error(ErrorCodeConfig.error_no_key, initialization.message))
            return
        }

        try {
            SDKCall.openByToken(
                activity,
                token,
                null,
                { result: CheckResult<String> ->
                    onEvent(result.toQlzSdkEvent())
                },
            )
        } catch (throwable: Throwable) {
            onEvent(
                QlzSdkEvent.Error(
                    ErrorCodeConfig.code_othererror,
                    throwable.message.toUserFacingEvaluationError(),
                )
            )
        }
    }

    fun openReport(
        activity: Activity,
        reportUrl: String,
    ) {
        if (reportUrl.isNotBlank()) {
            SDKCall.goResultAcitivty(activity, reportUrl)
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

    private fun CheckResult<String>.toQlzSdkEvent(): QlzSdkEvent =
        when (errorcode) {
            ErrorCodeConfig.code_res_success -> parseCompletedEvent(data)
            ErrorCodeConfig.code_check_state ->
                QlzSdkEvent.Progress(
                    successCount = CheckStateData.successCount,
                    totalCount = CheckStateData.allCount,
                )

            ErrorCodeConfig.code_finish_check -> QlzSdkEvent.DetectionPageClosed
            ErrorCodeConfig.code_finish_res -> QlzSdkEvent.ReportPageClosed
            ErrorCodeConfig.code_check_cancel -> QlzSdkEvent.Cancelled
            else ->
                QlzSdkEvent.Error(
                    code = errorcode,
                    message =
                        (
                            errorMsg?.takeIf { it.isNotBlank() }
                                ?: ErrorCodeConfig.getErrMsg(errorcode)
                        ).toUserFacingEvaluationError(),
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
                message = "评估结果读取失败，请稍后重试",
            )
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
    val message: String

    data object Ready : QlzSdkInitialization {
        override val message = "评估服务已准备"
    }

    data object MissingSdkKey : QlzSdkInitialization {
        override val message = "评估服务配置异常，请联系管理员"
    }

    data class Failed(
        override val message: String,
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

internal fun String?.toUserFacingEvaluationError(): String {
    val candidate = this?.trim().orEmpty()
    return candidate
        .takeIf {
            it.isNotBlank() &&
                !DEVELOPMENT_COPY_PATTERN.containsMatchIn(it)
        }
        ?: "评估暂时无法继续，请稍后重试"
}
