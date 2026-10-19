package com.ytone.longcare.domain.location

/** Only the authorized order identity is durable. Coordinates and HTTP requests are never stored. */
data class LocationAccount(val companyId: Int, val accountId: Int, val userId: Int)

data class LocationRecoveryRecord(
    val account: LocationAccount,
    val orderId: Long,
    val sessionId: String,
)

interface LocationRecoveryStore {
    suspend fun read(): LocationRecoveryRecord?
    suspend fun save(record: LocationRecoveryRecord)
    /** null clears all; an old order's delayed cleanup must not erase a new session. */
    suspend fun clear(sessionId: String? = null)
}
