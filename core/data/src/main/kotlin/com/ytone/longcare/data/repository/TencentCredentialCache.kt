package com.ytone.longcare.data.repository

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException

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
    private val stateLock = Any()

    @Volatile
    private var accessTokenEntry: CacheEntry<TencentAccessTokenResponse>? = null

    @Volatile
    private var signTicketEntry: CacheEntry<TencentApiTicketResponse>? = null

    fun clear() = synchronized(stateLock) {
        accessTokenEntry = null
        signTicketEntry = null
    }

    suspend fun getAccessToken(
        appId: String,
        sessionGeneration: Long,
        isCurrent: () -> Boolean,
        loader: suspend () -> ApiResult<TencentAccessTokenResponse>,
    ): ApiResult<TencentAccessTokenResponse> {
        requireCurrent(isCurrent)
        accessTokenEntry.validValue(appId, sessionGeneration)?.let { return ApiResult.Success(it) }
        return accessTokenMutex.withLock {
            requireCurrent(isCurrent)
            accessTokenEntry.validValue(appId, sessionGeneration)?.let { return@withLock ApiResult.Success(it) }
            val result = loader()
            synchronized(stateLock) {
                requireCurrent(isCurrent)
                val response = (result as? ApiResult.Success)?.data
                val expiresAt = cacheExpiration(response?.expireIn?.toLongOrNull())
                if (!response?.accessToken.isNullOrBlank() && expiresAt != null) {
                    accessTokenEntry = CacheEntry(appId, sessionGeneration, requireNotNull(response), expiresAt)
                }
                result
            }
        }
    }

    suspend fun getSignTicket(
        appId: String,
        sessionGeneration: Long,
        isCurrent: () -> Boolean,
        loader: suspend () -> ApiResult<TencentApiTicketResponse>,
    ): ApiResult<TencentApiTicketResponse> {
        requireCurrent(isCurrent)
        signTicketEntry.validValue(appId, sessionGeneration)?.let { return ApiResult.Success(it) }
        return signTicketMutex.withLock {
            requireCurrent(isCurrent)
            signTicketEntry.validValue(appId, sessionGeneration)?.let { return@withLock ApiResult.Success(it) }
            val result = loader()
            synchronized(stateLock) {
                requireCurrent(isCurrent)
                val response = (result as? ApiResult.Success)?.data
                val ticket = response?.tickets?.firstOrNull { it.value.isNotBlank() }
                val expiresAt = cacheExpiration(ticket?.expireIn?.toLongOrNull())
                if (ticket != null && expiresAt != null) {
                    signTicketEntry = CacheEntry(appId, sessionGeneration, response, expiresAt)
                }
                result
            }
        }
    }

    private fun requireCurrent(isCurrent: () -> Boolean) {
        if (!isCurrent()) throw CancellationException("Face verification session changed")
    }

    private fun <T> CacheEntry<T>?.validValue(key: String, sessionGeneration: Long): T? =
        this?.takeIf {
            it.key == key && it.sessionGeneration == sessionGeneration && nowMillis() < it.validUntilMillis
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
        val sessionGeneration: Long,
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
