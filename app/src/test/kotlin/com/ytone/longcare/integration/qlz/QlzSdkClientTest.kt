package com.ytone.longcare.integration.qlz

import android.app.Activity
import android.content.Context
import com.evenmed.sdk.call.ErrorCodeConfig
import com.ytone.longcare.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QlzSdkClientTest {
    private val context =
        mockk<Context>(relaxed = true) {
            every { getString(R.string.sales_error_evaluation_service_start) } returns
                "评估服务启动失败"
            every { getString(R.string.sales_error_evaluation_device_prepare_short) } returns
                "检测设备暂未准备好"
            every { getString(R.string.sales_error_evaluation_expired_short) } returns
                "评估凭证已失效"
            every { getString(R.string.sales_error_evaluation_continue) } returns
                "评估暂时无法继续"
            every { getString(R.string.sales_error_evaluation_result_read) } returns
                "评估结果读取失败"
            every { getString(R.string.sales_error_evaluation_service_configuration) } returns
                "评估服务配置缺失"
            every { getString(R.string.sales_evaluation_service_ready) } returns
                "评估服务已就绪"
        }

    @Test
    fun `blank runtime key fails before calling vendor code`() {
        val vendor = FakeQlzVendorApi()
        val client = createClient(key = "   ", vendor = vendor)

        assertEquals(QlzSdkInitialization.MissingSdkKey, client.initialize())
        assertTrue(vendor.operations.isEmpty())
        assertTrue(client.getDeviceId().isFailure)
        assertTrue(vendor.operations.isEmpty())
    }

    @Test
    fun `initialization trims config preserves call order and only initializes once`() {
        val vendor = FakeQlzVendorApi(deviceId = "device-17")
        val client = createClient(key = "  configured-key  ", testMode = true, vendor = vendor)

        assertEquals("device-17", client.getDeviceId().getOrThrow())
        assertEquals("device-17", client.getDeviceId().getOrThrow())

        assertEquals(
            listOf("setTestMode:true", "initialize", "getDeviceId", "getDeviceId"),
            vendor.operations,
        )
        assertEquals(1, vendor.initializationConfigs.size)
        assertEquals(
            QlzVendorInitializationConfig(
                sdkKey = "configured-key",
                httpTimeoutSeconds = 12,
                httpMaxRetry = 2,
                horizontalMode = false,
                bluetoothScanTimeoutMillis = 30_000L,
                autoConnectLastDevice = true,
                deviceConnectTimeoutSeconds = 15,
                checkIdleTimeoutSeconds = 180,
                autoOpenResult = false,
            ),
            vendor.initializationConfigs.single(),
        )
    }

    @Test
    fun `vendor initialization exception becomes recoverable business error`() {
        val vendor = FakeQlzVendorApi(initializeFailure = IllegalStateException("SDK key leaked"))
        val client = createClient(vendor = vendor)

        val result = client.initialize()

        assertEquals(
            QlzSdkInitialization.Failed("评估服务启动失败"),
            result,
        )
        assertFalse(result.toString().contains("SDK key leaked"))
        assertEquals(listOf("setTestMode:false", "initialize"), vendor.operations)
    }

    @Test
    fun `vendor callback maps progress completion cancellation closes and parse failure`() {
        val vendor =
            FakeQlzVendorApi(
                results =
                    mutableListOf(
                        QlzVendorResult(
                            errorCode = ErrorCodeConfig.code_check_state,
                            errorMessage = null,
                            data = null,
                            successCount = 2,
                            totalCount = 5,
                        ),
                        QlzVendorResult(
                            errorCode = ErrorCodeConfig.code_res_success,
                            errorMessage = null,
                            data = """{"recordid":"record-1","url":"https://report","score1":"88"}""",
                        ),
                        QlzVendorResult(ErrorCodeConfig.code_check_cancel, null, null),
                        QlzVendorResult(ErrorCodeConfig.code_finish_check, null, null),
                        QlzVendorResult(ErrorCodeConfig.code_finish_res, null, null),
                        QlzVendorResult(
                            errorCode = ErrorCodeConfig.code_res_success,
                            errorMessage = null,
                            data = "not-json",
                        ),
                        QlzVendorResult(
                            errorCode = ErrorCodeConfig.code_othererror,
                            errorMessage = "Token=technical-value",
                            data = null,
                        ),
                    )
            )
        val client = createClient(vendor = vendor)
        val events = mutableListOf<QlzSdkEvent>()

        client.openByToken(mockk<Activity>(relaxed = true), "one-time-token", events::add)

        assertEquals(
            listOf(
                QlzSdkEvent.Progress(2, 5),
                QlzSdkEvent.Completed("record-1", "https://report", "88"),
                QlzSdkEvent.Cancelled,
                QlzSdkEvent.DetectionPageClosed,
                QlzSdkEvent.ReportPageClosed,
                QlzSdkEvent.Error(
                    ErrorCodeConfig.error_server_gson,
                    "评估结果读取失败",
                ),
                QlzSdkEvent.Error(
                    ErrorCodeConfig.code_othererror,
                    "评估暂时无法继续",
                ),
            ),
            events,
        )
        assertFalse(events.joinToString().contains("technical-value"))
    }

    @Test
    fun `blank token and page invocation exception never report success`() {
        val vendor = FakeQlzVendorApi(openFailure = IllegalStateException("SDK URL failed"))
        val client = createClient(vendor = vendor)
        val events = mutableListOf<QlzSdkEvent>()
        val activity = mockk<Activity>(relaxed = true)

        client.openByToken(activity, "  ", events::add)
        client.openByToken(activity, "valid-token", events::add)

        assertEquals(2, events.size)
        assertTrue(events.all { it is QlzSdkEvent.Error })
        assertEquals(1, vendor.openCount)
        assertFalse(events.joinToString().contains("SDK URL"))
    }

    private fun createClient(
        key: String = "configured-key",
        testMode: Boolean = false,
        vendor: FakeQlzVendorApi,
    ): QlzSdkClient =
        QlzSdkClient(
            appContext = context,
            runtimeConfig = QlzSdkRuntimeConfig(key, testMode),
            vendorApi = vendor,
        )
}

private class FakeQlzVendorApi(
    private val deviceId: String? = "device-1",
    private val connectedDeviceName: String? = "QLZ device",
    private val initializeFailure: Throwable? = null,
    private val openFailure: Throwable? = null,
    private val results: MutableList<QlzVendorResult> = mutableListOf(),
) : QlzVendorApi {
    val operations = mutableListOf<String>()
    val initializationConfigs = mutableListOf<QlzVendorInitializationConfig>()
    var openCount: Int = 0
        private set

    override fun setTestMode(enabled: Boolean) {
        operations += "setTestMode:$enabled"
    }

    override fun initialize(context: Context, config: QlzVendorInitializationConfig) {
        operations += "initialize"
        initializationConfigs += config
        initializeFailure?.let { throw it }
    }

    override fun getDeviceId(context: Context): String? {
        operations += "getDeviceId"
        return deviceId
    }

    override fun getConnectedDeviceName(): String? {
        operations += "getConnectedDeviceName"
        return connectedDeviceName
    }

    override fun openByToken(
        activity: Activity,
        token: String,
        onResult: (QlzVendorResult) -> Unit,
    ) {
        operations += "openByToken"
        openCount += 1
        openFailure?.let { throw it }
        results.forEach(onResult)
    }
}
