package com.ytone.longcare.domain.startup

/**
 * Process-wide gate that must finish the one-time legacy-state cutover before any session or
 * user namespace can become visible.
 */
interface UserStorageNamespaceCutoverGate {
    val isCompleted: Boolean

    suspend fun ensureCompleted()
}
