package com.ytone.longcare.data.repository

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 腾讯 Access Token 与 SIGN ticket 的进程内单航班缓存。
 *
 * NONCE ticket 与具体用户及单次核验绑定，不能复用，因此不进入该缓存。
 */
internal class TencentCredentialCache(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val accessTokenMutex = Mutex()
    private val signTicketMutex = Mutex()

    @Volatile
    private var accessTokenEntry: CacheEntry<TencentAccessTokenResponse>? = null

    @Volatile
    private var signTicketEntry: CacheEntry<TencentApiTicketResponse>? = null

    suspend fun getAccessToken(
        appId: String,
        loader: suspend () -> ApiResult<TencentAccessTokenResponse>,
    ): ApiResult<TencentAccessTokenResponse> {
        accessTokenEntry.validValue(appId)?.let { return ApiResult.Success(it) }
        return accessTokenMutex.withLock {
            accessTokenEntry.validValue(appId)?.let { return@withLock ApiResult.Success(it) }
            loader().also { result ->
                val response = (result as? ApiResult.Success)?.data ?: return@also
                val hasToken = !response.accessToken.isNullOrBlank()
                val expiresAt = cacheExpiration(response.expireIn?.toLongOrNull())
                if (hasToken && expiresAt != null) {
                    accessTokenEntry =
                        CacheEntry(
                            key = appId,
                            value = response,
                            validUntilMillis = expiresAt,
                        )
                }
            }
        }
    }

    suspend fun getSignTicket(
        appId: String,
        loader: suspend () -> ApiResult<TencentApiTicketResponse>,
    ): ApiResult<TencentApiTicketResponse> {
        signTicketEntry.validValue(appId)?.let { return ApiResult.Success(it) }
        return signTicketMutex.withLock {
            signTicketEntry.validValue(appId)?.let { return@withLock ApiResult.Success(it) }
            loader().also { result ->
                val response = (result as? ApiResult.Success)?.data ?: return@also
                val ticket = response.tickets?.firstOrNull { it.value.isNotBlank() }
                val expiresAt = cacheExpiration(ticket?.expireIn?.toLongOrNull())
                if (ticket != null && expiresAt != null) {
                    signTicketEntry =
                        CacheEntry(
                            key = appId,
                            value = response,
                            validUntilMillis = expiresAt,
                        )
                }
            }
        }
    }

    private fun <T> CacheEntry<T>?.validValue(key: String): T? =
        this?.takeIf {
            it.key == key && nowMillis() < it.validUntilMillis
        }?.value

    private fun cacheExpiration(expireInSeconds: Long?): Long? {
        if (expireInSeconds == null || expireInSeconds <= 0L) {
            return null
        }
        val ttlMillis =
            expireInSeconds
                .coerceAtMost(Long.MAX_VALUE / MILLIS_PER_SECOND)
                .times(MILLIS_PER_SECOND)
        val safetyWindow =
            (ttlMillis / SAFETY_WINDOW_DIVISOR)
                .coerceIn(MIN_SAFETY_WINDOW_MILLIS, MAX_SAFETY_WINDOW_MILLIS)
                .coerceAtMost(ttlMillis)
        val usableTtl = ttlMillis - safetyWindow
        if (usableTtl <= 0L) {
            return null
        }
        return nowMillis() + usableTtl
    }

    private data class CacheEntry<T>(
        val key: String,
        val value: T,
        val validUntilMillis: Long,
    )

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val SAFETY_WINDOW_DIVISOR = 10L
        const val MIN_SAFETY_WINDOW_MILLIS = 1_000L
        const val MAX_SAFETY_WINDOW_MILLIS = 60_000L
    }
}
