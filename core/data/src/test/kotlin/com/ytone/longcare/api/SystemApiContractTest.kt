package com.ytone.longcare.api

import com.squareup.moshi.Moshi
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.RequestSessionGeneration
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.data.repository.SystemRepositoryImpl
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.mockk
import io.mockk.verify
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

class SystemApiContractTest {

    @Test
    fun `successful null or missing version data means no update`() = runTest {
        for (json in listOf(
            """{"resultCode":1000,"resultMsg":"ok","data":null}""",
            """{"resultCode":1000,"resultMsg":"ok"}""",
        )) {
            val repository = SystemRepositoryImpl(api(json))

            assertEquals(ApiResult.Success(null), repository.checkVersion())
        }
    }

    @Test
    fun `version check preserves its request and complete version payload`() = runTest {
        var request: Request? = null
        val repository = SystemRepositoryImpl(api(
            responseJson = """
                {"resultCode":1000,"resultMsg":"ok","data":{
                  "versionCode":79,"versionName":"1.0.8","upType":2,
                  "remarks":"更新说明","platform":"android",
                  "downUrl":"https://example.test/app.apk"
                }}
            """.trimIndent(),
            recordRequest = { request = it },
        ))

        assertEquals(
            ApiResult.Success(AppVersionModel(
                versionCode = 79,
                versionName = "1.0.8",
                upType = 2,
                remarks = "更新说明",
                platform = "android",
                downUrl = "https://example.test/app.apk",
            )),
            repository.checkVersion(),
        )
        val sentRequest = requireNotNull(request)
        assertEquals("GET", sentRequest.method)
        assertEquals("/V1/System/ChecVersion", sentRequest.url.encodedPath)
        assertNull(sentRequest.url.encodedQuery)
        assertNull(sentRequest.body)
    }

    @Test
    fun `empty or malformed responses are not treated as no update`() = runTest {
        for ((status, json) in listOf(
            200 to "",
            204 to "",
            200 to "null",
            200 to "{",
            200 to """{"resultCode":1000,"resultMsg":"ok","data":[]}""",
            200 to """{"resultCode":1000,"resultMsg":"ok","data":{"versionCode":"invalid"}}""",
            200 to """{"resultMsg":"ok","data":null}""",
        )) {
            assertRequestError(
                api(json, status).checkVersion(),
                ApiRequestException.Kind.INVALID_RESPONSE,
            )
        }
    }

    @Test
    fun `http errors take precedence over a successful empty envelope`() = runTest {
        assertRequestError(
            api("""{"resultCode":1000,"resultMsg":"ok","data":null}""", 503).checkVersion(),
            ApiRequestException.Kind.HTTP,
            httpCode = 503,
        )
    }

    @Test
    fun `business failures preserve session invalidation for version checks`() = runTest {
        for (code in listOf(4001, 1001, 3002)) {
            val handler = mockk<SessionInvalidationHandler>(relaxed = true)
            val service = api(
                """{"resultCode":$code,"resultMsg":"failure","data":null}""",
                sessionHandler = handler,
            )

            assertEquals(ApiResult.Failure(code, "failure"), service.checkVersion())
            verify(exactly = if (code == 4001) 0 else 1) {
                handler.invalidate("failure", 1L)
            }
        }
    }

    @Test
    fun `other endpoints still reject null or missing data`() = runTest {
        for (json in listOf(
            """{"resultCode":1000,"resultMsg":"ok","data":null}""",
            """{"resultCode":1000,"resultMsg":"ok"}""",
        )) {
            assertRequestError(api(json).getStartConfig(), ApiRequestException.Kind.INVALID_RESPONSE)
        }
    }

    private fun assertRequestError(
        result: ApiResult<*>,
        kind: ApiRequestException.Kind,
        httpCode: Int? = null,
    ) {
        assertTrue("Expected request error, got $result", result is ApiResult.Exception)
        val error = (result as ApiResult.Exception).exception
        assertTrue(error is ApiRequestException)
        assertEquals(kind, (error as ApiRequestException).kind)
        assertEquals(httpCode, error.httpCode)
    }

    /** Exercise the real Retrofit/Moshi contract without contacting the backend. */
    private fun api(
        responseJson: String,
        status: Int = 200,
        sessionHandler: SessionInvalidationHandler = mockk(relaxed = true),
        recordRequest: (Request) -> Unit = {},
    ): LongCareApiService = Retrofit.Builder()
        .baseUrl("https://example.test/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            recordRequest(chain.request())
            Response.Builder()
                .request(chain.request().newBuilder()
                    .tag(RequestSessionGeneration::class.java, RequestSessionGeneration(1L)).build())
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message("test response")
                .body(responseJson.toResponseBody("application/json".toMediaType()))
                .build()
        }.build())
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .addCallAdapterFactory(ApiResultCallAdapterFactory(sessionHandler))
        .build()
        .create(LongCareApiService::class.java)
}
