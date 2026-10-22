package com.ytone.longcare.network

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.squareup.moshi.Moshi
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.RequestSessionGeneration
import com.ytone.longcare.data.repository.DefaultSessionInvalidationHandler
import com.ytone.longcare.data.repository.DefaultUserSessionRepository
import com.ytone.longcare.data.repository.UserSessionTracker
import com.ytone.longcare.model.User
import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

/** Local interceptor/Retrofit integration, not real backend or device-network evidence. */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionInvalidationRequestIntegrationTest {
    @Test fun `old dispatched response cannot clear another account or a same token relogin`() = runTest {
        val initial = User(userId = 7, token = "local-test-token")
        for (replacement in listOf(User(userId = 8, token = "next-local-token"), initial)) {
            val store = object : DataStore<Preferences> {
                private val values = MutableStateFlow(emptyPreferences())
                override val data = values
                override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                    transform(values.value).also { values.value = it }
            }
            val repository = DefaultUserSessionRepository(store, backgroundScope, UserSessionTracker())
            repository.login(initial)
            val handler = DefaultSessionInvalidationHandler(repository, backgroundScope)
            val dispatched = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val sent = repository.sessionSnapshot.value
                val request = chain.request().newBuilder()
                    .tag(RequestSessionGeneration::class.java, RequestSessionGeneration(sent.generation)).build()
                dispatched.complete(Unit)
                runBlocking { release.await() }
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"resultCode":3002,"resultMsg":"expired","data":null}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
            val api = Retrofit.Builder().baseUrl("https://example.test/").client(client)
                .addCallAdapterFactory(ApiResultCallAdapterFactory(handler))
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
                .build().create(InvalidationApi::class.java)
            try {
                val response = async { api.expired() }
                dispatched.await()
                repository.login(replacement)
                release.complete(Unit)
                assertEquals(ApiResult.Failure(3002, "expired"), response.await())
                runCurrent()
                assertEquals(replacement, repository.sessionSnapshot.value.user)
                assertNull(handler.invalidations.value)
            } finally {
                release.complete(Unit)
                client.dispatcher.executorService.shutdown()
                client.connectionPool.evictAll()
            }
        }
    }
}

private interface InvalidationApi {
    @GET("expired") suspend fun expired(): ApiResult<String>
}
