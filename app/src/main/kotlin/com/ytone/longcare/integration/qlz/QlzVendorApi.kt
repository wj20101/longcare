package com.ytone.longcare.integration.qlz

import android.app.Activity
import android.content.Context
import com.evenmed.sdk.call.CheckConfig
import com.evenmed.sdk.call.CheckIml
import com.evenmed.sdk.call.CheckResult
import com.evenmed.sdk.call.CheckStateData
import com.evenmed.sdk.call.ErrorCodeConfig
import com.evenmed.sdk.call.SDKCall

/** Narrow app-owned boundary around the public static APIs exposed by the approved vendor AAR. */
internal interface QlzVendorApi {
    fun setTestMode(enabled: Boolean)

    fun initialize(context: Context, config: QlzVendorInitializationConfig)

    fun getDeviceId(context: Context): String?

    fun getConnectedDeviceName(): String?

    fun openByToken(
        activity: Activity,
        token: String,
        onResult: (QlzVendorResult) -> Unit,
    )
}

internal data class QlzVendorInitializationConfig(
    val sdkKey: String,
    val httpTimeoutSeconds: Int,
    val httpMaxRetry: Int,
    val horizontalMode: Boolean,
    val bluetoothScanTimeoutMillis: Long,
    val autoConnectLastDevice: Boolean,
    val deviceConnectTimeoutSeconds: Int,
    val checkIdleTimeoutSeconds: Int,
    val autoOpenResult: Boolean,
)

internal data class QlzVendorResult(
    val errorCode: Int,
    val errorMessage: String?,
    val data: String?,
    val successCount: Int = 0,
    val totalCount: Int = 0,
)

internal object AndroidQlzVendorApi : QlzVendorApi {
    override fun setTestMode(enabled: Boolean) {
        CheckIml.setTestMode(enabled)
    }

    override fun initialize(context: Context, config: QlzVendorInitializationConfig) {
        val vendorConfig =
            CheckConfig.Builder()
                .setSdkKey(config.sdkKey)
                .setHttpConfig(config.httpTimeoutSeconds, config.httpMaxRetry)
                .setHorizontalMode(config.horizontalMode)
                .setBlueScanTime(config.bluetoothScanTimeoutMillis)
                .setAutoConnectLastDevice(config.autoConnectLastDevice)
                .setConnectDeviceOutTime(config.deviceConnectTimeoutSeconds)
                .setCheckNullOutTime(config.checkIdleTimeoutSeconds)
                .setAutoOpenRes(config.autoOpenResult)
                .build()
        CheckIml.init(context, vendorConfig)
    }

    override fun getDeviceId(context: Context): String? = CheckIml.getDeviceId(context)

    override fun getConnectedDeviceName(): String? = CheckIml.getBlueDeviceName()

    override fun openByToken(
        activity: Activity,
        token: String,
        onResult: (QlzVendorResult) -> Unit,
    ) {
        SDKCall.openByToken(
            activity,
            token,
            null,
            { result: CheckResult<String> ->
                onResult(
                    QlzVendorResult(
                        errorCode = result.errorcode,
                        errorMessage = result.errorMsg,
                        data = result.data,
                        successCount =
                            if (result.errorcode == ErrorCodeConfig.code_check_state) {
                                CheckStateData.successCount
                            } else {
                                0
                            },
                        totalCount =
                            if (result.errorcode == ErrorCodeConfig.code_check_state) {
                                CheckStateData.allCount
                            } else {
                                0
                            },
                    )
                )
            },
        )
    }
}
