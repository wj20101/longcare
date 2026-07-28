package com.ytone.longcare.data.repository

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.TicketInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TencentCredentialCacheTest {

    @Test
    fun `access token is reused until its safety-adjusted expiry`() = runTest {
        var now = 1_000L
        var loads = 0
        val cache = TencentCredentialCache(nowMillis = { now })
        val loader = suspend {
            loads += 1
            ApiResult.Success(accessToken("token-$loads", expireIn = "100"))
        }

        val first = cache.getAccessToken("app-id", loader)
        now += 89_000L
        val cached = cache.getAccessToken("app-id", loader)
        now += 1_000L
        val refreshed = cache.getAccessToken("app-id", loader)

        assertEquals("token-1", first.successData().accessToken)
        assertEquals("token-1", cached.successData().accessToken)
        assertEquals("token-2", refreshed.successData().accessToken)
        assertEquals(2, loads)
    }

    @Test
    fun `concurrent access token requests share one refresh`() = runTest {
        var loads = 0
        val loaderStarted = CompletableDeferred<Unit>()
        val releaseLoader = CompletableDeferred<Unit>()
        val cache = TencentCredentialCache()

        val requests =
            List(8) {
                async {
                    cache.getAccessToken("app-id") {
                        loads += 1
                        loaderStarted.complete(Unit)
                        releaseLoader.await()
                        ApiResult.Success(accessToken("shared-token", expireIn = "3600"))
                    }
                }
            }

        loaderStarted.await()
        releaseLoader.complete(Unit)
        val results = requests.awaitAll()

        assertEquals(1, loads)
        assertTrue(
            results.all {
                (it as? ApiResult.Success)?.data?.accessToken == "shared-token"
            }
        )
    }

    @Test
    fun `sign ticket is cached but nonce tickets remain outside this cache`() = runTest {
        var loads = 0
        val cache = TencentCredentialCache()
        val loader = suspend {
            loads += 1
            ApiResult.Success(signTicket("sign-$loads", expireIn = "3600"))
        }

        val first = cache.getSignTicket("app-id", loader)
        val second = cache.getSignTicket("app-id", loader)

        assertEquals("sign-1", first.successData().tickets?.single()?.value)
        assertEquals("sign-1", second.successData().tickets?.single()?.value)
        assertEquals(1, loads)
    }

    private fun accessToken(
        value: String,
        expireIn: String,
    ) = TencentAccessTokenResponse(
        code = "0",
        msg = "ok",
        transactionTime = "2026-07-28T10:00:00",
        accessToken = value,
        expireIn = expireIn,
    )

    private fun signTicket(
        value: String,
        expireIn: String,
    ) = TencentApiTicketResponse(
        code = "0",
        msg = "ok",
        transactionTime = "2026-07-28T10:00:00",
        tickets =
            listOf(
                TicketInfo(
                    value = value,
                    expireTime = "2026-07-28T11:00:00",
                    expireIn = expireIn,
                )
            ),
    )

    private fun <T> ApiResult<T>.successData(): T =
        (this as ApiResult.Success).data
}
