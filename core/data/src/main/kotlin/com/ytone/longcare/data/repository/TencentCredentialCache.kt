package com.ytone.longcare.data.repository

import com.ytone.longcare.model.TencentAccessTokenResponse
import com.ytone.longcare.model.TencentApiTicketResponse
import com.ytone.longcare.model.result.ApiResult
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Session-scoped single-flight cache for Tencent temporary authorization. */
internal class TencentCredentialCache(
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private data class CacheKey(val sessionFingerprint: String, val appId: String)
    private data class CacheEntry<T>(val value: T, val validUntilMillis: Long)

    private val revision = AtomicLong()
    private val accessTokenEntries = ConcurrentHashMap<CacheKey, CacheEntry<TencentAccessTokenResponse>>()
    private val signTicketEntries = ConcurrentHashMap<CacheKey, CacheEntry<TencentApiTicketResponse>>()
    private val accessTokenMutexes = ConcurrentHashMap<CacheKey, Mutex>()
    private val signTicketMutexes = ConcurrentHashMap<CacheKey, Mutex>()

    suspend fun getAccessToken(
        sessionFingerprint: String,
        appId: String,
        loader: suspend () -> ApiResult<TencentAccessTokenResponse>,
    ): ApiResult<TencentAccessTokenResponse> {
        val key = CacheKey(sessionFingerprint, appId)
        accessTokenEntries[key].validValue()?.let { return ApiResult.Success(it) }
        val capturedRevision = revision.get()
        return accessTokenMutexes.getOrPut(key) { Mutex() }.withLock {
            accessTokenEntries[key].validValue()?.let { return@withLock ApiResult.Success(it) }
            loader().also { result ->
                val response = (result as? ApiResult.Success)?.data ?: return@also
                val expiresAt = cacheExpiration(response.expireIn?.toLongOrNull())
                if (!response.accessToken.isNullOrBlank() && expiresAt != null && revision.get() == capturedRevision) {
                    accessTokenEntries[key] = CacheEntry(response, expiresAt)
                    if (revision.get() != capturedRevision) accessTokenEntries.remove(key)
                }
            }
        }
    }

    suspend fun getSignTicket(
        sessionFingerprint: String,
        appId: String,
        loader: suspend () -> ApiResult<TencentApiTicketResponse>,
    ): ApiResult<TencentApiTicketResponse> {
        val key = CacheKey(sessionFingerprint, appId)
        signTicketEntries[key].validValue()?.let { return ApiResult.Success(it) }
        val capturedRevision = revision.get()
        return signTicketMutexes.getOrPut(key) { Mutex() }.withLock {
            signTicketEntries[key].validValue()?.let { return@withLock ApiResult.Success(it) }
            loader().also { result ->
                val response = (result as? ApiResult.Success)?.data ?: return@also
                val ticket = response.tickets?.firstOrNull { it.value.isNotBlank() }
                val expiresAt = cacheExpiration(ticket?.expireIn?.toLongOrNull())
                if (ticket != null && expiresAt != null && revision.get() == capturedRevision) {
                    signTicketEntries[key] = CacheEntry(response, expiresAt)
                    if (revision.get() != capturedRevision) signTicketEntries.remove(key)
                }
            }
        }
    }

    fun clear() {
        revision.incrementAndGet()
        accessTokenEntries.clear()
        signTicketEntries.clear()
        accessTokenMutexes.clear()
        signTicketMutexes.clear()
    }

    private fun <T> CacheEntry<T>?.validValue(): T? =
        this?.takeIf { nowMillis() < it.validUntilMillis }?.value

    private fun cacheExpiration(expireInSeconds: Long?): Long? {
        if (expireInSeconds == null || expireInSeconds <= 0L) return null
        val ttlMillis = expireInSeconds
            .coerceAtMost(Long.MAX_VALUE / MILLIS_PER_SECOND)
            .times(MILLIS_PER_SECOND)
        val safetyWindow = (ttlMillis / SAFETY_WINDOW_DIVISOR)
            .coerceIn(MIN_SAFETY_WINDOW_MILLIS, MAX_SAFETY_WINDOW_MILLIS)
            .coerceAtMost(ttlMillis)
        val usableTtl = ttlMillis - safetyWindow
        return if (usableTtl > 0L) nowMillis() + usableTtl else null
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val SAFETY_WINDOW_DIVISOR = 10L
        const val MIN_SAFETY_WINDOW_MILLIS = 1_000L
        const val MAX_SAFETY_WINDOW_MILLIS = 60_000L
    }
}
