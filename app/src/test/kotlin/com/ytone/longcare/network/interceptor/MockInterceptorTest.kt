package com.ytone.longcare.network.interceptor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `known route returns local response without proceeding`() {
        val request = get("/V1/System/Start")
        val chain = chain(request)

        val response = MockInterceptor(context, mockEnabled = true).intercept(chain)

        assertThat(response.code).isEqualTo(200)
        assertThat(response.body.string()).contains("userXieYiUrl")
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `unknown route fails locally without proceeding`() {
        val request = get("/V1/Unknown")
        val chain = chain(request)

        val failure = runCatching {
            MockInterceptor(context, mockEnabled = true).intercept(chain)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(MissingMockRouteException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `wrong method fails locally without proceeding`() {
        val request = post("/V1/System/Start")
        val chain = chain(request)

        val failure = runCatching {
            MockInterceptor(context, mockEnabled = true).intercept(chain)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(MissingMockRouteException::class.java)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    @Test
    fun `disabled mock proceeds exactly once and preserves response`() {
        val request = get("/V1/Unknown")
        val expected = response(request, code = 204, message = "Real integration response")
        val chain = chain(request)
        every { chain.proceed(request) } returns expected

        val actual = MockInterceptor(context, mockEnabled = false).intercept(chain)

        assertThat(actual).isSameInstanceAs(expected)
        verify(exactly = 1) { chain.proceed(request) }
    }

    @Test
    fun `explicit first party mock never takes over amap or vendor client origins`() {
        listOf(
            "https://restapi.amap.com/V1/System/Start",
            "https://kyc1.qcloud.com/V1/System/Start",
        ).forEach { url ->
            val request = Request.Builder().url(url).get().build()
            val expected = response(request, code = 503, message = "Vendor offline")
            val chain = chain(request)
            every { chain.proceed(request) } returns expected

            val actual = MockInterceptor(context, mockEnabled = true).intercept(chain)

            assertThat(actual).isSameInstanceAs(expected)
            verify(exactly = 1) { chain.proceed(request) }
        }
    }

    @Test
    fun `missing route diagnostic excludes request secrets and query`() {
        val request = Request.Builder()
            .url("https://careapi.ytone.cn/V1/Unknown?token=query-secret")
            .header("Authorization", "Bearer header-secret")
            .header("Cookie", "session=cookie-secret")
            .post("user-private-body".toRequestBody())
            .build()
        val chain = chain(request)

        val failure = runCatching {
            MockInterceptor(context, mockEnabled = true).intercept(chain)
        }.exceptionOrNull()

        assertThat(failure?.message).isEqualTo("Missing debug mock route: POST /V1/Unknown")
        assertThat(failure?.message).doesNotContain("query-secret")
        assertThat(failure?.message).doesNotContain("header-secret")
        assertThat(failure?.message).doesNotContain("cookie-secret")
        assertThat(failure?.message).doesNotContain("user-private-body")
        verify(exactly = 0) { chain.proceed(any()) }
    }

    private fun chain(request: Request): Interceptor.Chain =
        mockk<Interceptor.Chain>(relaxed = true).also { chain ->
            every { chain.request() } returns request
        }

    private fun get(path: String): Request = Request.Builder()
        .url("https://careapi.ytone.cn$path")
        .get()
        .build()

    private fun post(path: String): Request = Request.Builder()
        .url("https://careapi.ytone.cn$path")
        .post("{}".toRequestBody())
        .build()

    private fun response(
        request: Request,
        code: Int,
        message: String,
    ): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body("".toResponseBody())
        .build()
}
