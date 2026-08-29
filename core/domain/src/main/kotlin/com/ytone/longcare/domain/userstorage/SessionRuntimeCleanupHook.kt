package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.UserScopeKey

/** Identifies the session whose process-owned runtime resources must be stopped. */
data class SessionRuntimeIdentity(
    val scopeKey: UserScopeKey,
    val sessionEpoch: SessionEpoch,
)

/**
 * Stops process-owned capabilities before another user namespace can become Ready.
 *
 * Implementations must be idempotent. A thrown failure prevents account activation; a
 * [kotlinx.coroutines.CancellationException] is never converted into ordinary success.
 */
fun interface SessionRuntimeCleanupHook {
    suspend fun cleanup(identity: SessionRuntimeIdentity)
}
