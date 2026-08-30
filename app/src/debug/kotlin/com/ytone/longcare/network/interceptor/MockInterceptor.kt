package com.ytone.longcare.network.interceptor

import android.content.Context
import com.ytone.longcare.BuildConfig
import okhttp3.Interceptor
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl

internal class MockInterceptor(
    context: Context,
    private val mockEnabled: Boolean = BuildConfig.USE_MOCK_DATA,
    private val firstPartyBaseUrl: HttpUrl = BuildConfig.BASE_URL.toHttpUrl(),
    private val scenarioProvider: MockScenarioProvider = DefaultMockScenarioProvider,
    private val routeRegistry: MockRouteRegistry = MockRouteRegistry.create(
        MockAssetLoader { assetPath ->
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }
    ),
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!mockEnabled) {
            return chain.proceed(request)
        }
        if (!request.url.isSameOrigin(firstPartyBaseUrl)) {
            return chain.proceed(request)
        }

        val route = routeRegistry.find(request)
            ?: throw MissingMockRouteException(MockRouteKey.from(request))
        val mockJson = routeRegistry.responseBody(route, request, scenarioProvider)

        return Response.Builder()
            .code(200)
            .message("OK (Mocked)")
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .body(mockJson.toResponseBody(JSON_MEDIA_TYPE))
            .addHeader("content-type", JSON_MEDIA_TYPE.toString())
            .build()
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

private fun HttpUrl.isSameOrigin(baseUrl: HttpUrl): Boolean =
    scheme == baseUrl.scheme && host == baseUrl.host && port == baseUrl.port
