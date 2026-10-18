package com.ytone.longcare.api

import com.squareup.moshi.Moshi
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.SessionInvalidation
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class HomeDeviceApiContractTest {

    @Test
    fun `device status sends documented parameterless GET and decodes all five fields`() = runTest {
        var request: Request? = null
        val api = createApi(
            responseJson = """
                {"resultCode":1000,"resultMsg":"成功","data":{
                  "deviceSn":"SN-123","statusColor":"#36A675","status":"设备在线",
                  "installUrl":"https://example.test/install","previewUrl":"https://example.test/preview"
                }}
            """.trimIndent(),
            recordRequest = { request = it },
        )

        val result = api.getZhiChiDeviceStatus()

        assertEquals(
            ApiResult.Success(
                ZhiChiDeviceStatusModel(
                    deviceSn = "SN-123",
                    statusColor = "#36A675",
                    status = "设备在线",
                    installUrl = "https://example.test/install",
                    previewUrl = "https://example.test/preview",
                ),
            ),
            result,
        )
        val sentRequest = requireNotNull(request)
        assertEquals("GET", sentRequest.method)
        assertEquals("/V1/User/ZhiChiDeviceStatus", sentRequest.url.encodedPath)
        assertNull(sentRequest.url.encodedQuery)
        assertNull(sentRequest.body)
        assertTrue(sentRequest.cacheControl.noCache)
        assertTrue(sentRequest.cacheControl.noStore)
    }

    @Test
    fun `missing optional device fields decode to empty strings`() = runTest {
        val api = createApi("""{"resultCode":1000,"resultMsg":"成功","data":{}}""")

        assertEquals(ApiResult.Success(ZhiChiDeviceStatusModel()), api.getZhiChiDeviceStatus())
    }

    @Test
    fun `device polling preserves global session invalidation`() = runTest {
        val handler = DeviceSessionInvalidationHandler()
        val api = createApi(
            responseJson = """{"resultCode":3002,"resultMsg":"登录过期","data":null}""",
            sessionHandler = handler,
        )

        assertEquals(ApiResult.Failure(3002, "登录过期"), api.getZhiChiDeviceStatus())
        assertEquals(listOf("登录过期"), handler.reasons)
    }

    /** Intercept locally so this tests Retrofit's real wire contract without contacting the API. */
    private fun createApi(
        responseJson: String,
        sessionHandler: SessionInvalidationHandler = DeviceSessionInvalidationHandler(),
        recordRequest: (Request) -> Unit = {},
    ): LongCareApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                recordRequest(chain.request())
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseJson.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
        return Retrofit.Builder()
            .baseUrl("https://example.test/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
            .addCallAdapterFactory(ApiResultCallAdapterFactory(sessionHandler))
            .build()
            .create(LongCareApiService::class.java)
    }
}

private class DeviceSessionInvalidationHandler : SessionInvalidationHandler {
    override val invalidations: StateFlow<SessionInvalidation?> = MutableStateFlow(null)
    val reasons = mutableListOf<String>()

    override fun invalidate(reason: String) {
        reasons += reason
    }

    override fun consume(id: Long) = Unit
}
