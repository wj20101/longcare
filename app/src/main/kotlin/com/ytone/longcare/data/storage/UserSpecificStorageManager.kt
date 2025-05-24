package com.ytone.longcare.data.storage

import com.tencent.mmkv.MMKV
import com.ytone.longcare.domain.repository.UserSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages user-specific MMKV instances.
 *
 * This class observes the current user ID from [UserSessionRepository] and provides
 * an MMKV instance that is specific to that user. If no user is logged in,
 * it might return null or a default non-user-specific MMKV instance, depending on the desired behavior.
 */
@Singleton
class UserSpecificStorageManager @Inject constructor(
    private val userSessionRepository: UserSessionRepository
) {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * A [StateFlow] that emits the MMKV instance for the currently logged-in user.
     * Emits `null` if no user is logged in or if MMKV initialization fails for a user.
     * The MMKV instance is identified by the user's ID.
     */
    val userMMKV: StateFlow<MMKV?> = userSessionRepository.currentUserId
        .map { userId ->
            if (userId != null) {
                // Create or get an MMKV instance with mmapID = userId
                // This ensures data isolation between users.
                MMKV.mmkvWithID(userId)
            } else {
                null // No user logged in, so no user-specific MMKV instance.
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep alive for 5s after last subscriber
            initialValue = null // Initially no user MMKV instance
        )

    // Convenience methods to interact with the user-specific MMKV instance
    // These will operate on the MMKV instance of the currently logged-in user.
    // If no user is logged in, these operations might do nothing or throw an exception,
    // depending on how you want to handle it. Here, they will do nothing if userMMKV.value is null.

    fun putString(key: String, value: String) {
        userMMKV.value?.encode(key, value)
    }

    fun getString(key: String, defaultValue: String? = null): String? {
        return userMMKV.value?.decodeString(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        userMMKV.value?.encode(key, value)
    }

    fun getInt(key: String, defaultValue: Int = 0): Int {
        return userMMKV.value?.decodeInt(key, defaultValue) ?: defaultValue
    }

    fun putBoolean(key: String, value: Boolean) {
        userMMKV.value?.encode(key, value)
    }

    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        return userMMKV.value?.decodeBool(key, defaultValue) ?: defaultValue
    }

    fun putLong(key: String, value: Long) {
        userMMKV.value?.encode(key, value)
    }

    fun getLong(key: String, defaultValue: Long = 0L): Long {
        return userMMKV.value?.decodeLong(key, defaultValue) ?: defaultValue
    }

    fun putFloat(key: String, value: Float) {
        userMMKV.value?.encode(key, value)
    }

    fun getFloat(key: String, defaultValue: Float = 0f): Float {
        return userMMKV.value?.decodeFloat(key, defaultValue) ?: defaultValue
    }

    fun putByteArray(key: String, value: ByteArray) {
        userMMKV.value?.encode(key, value)
    }

    fun getByteArray(key: String, defaultValue: ByteArray? = null): ByteArray? {
        return userMMKV.value?.decodeBytes(key, defaultValue)
    }

    fun putStringSet(key: String, value: Set<String>) {
        userMMKV.value?.encode(key, value)
    }

    fun getStringSet(key: String, defaultValue: Set<String>? = null): Set<String>? {
        return userMMKV.value?.decodeStringSet(key, defaultValue)
    }

    fun remove(key: String) {
        userMMKV.value?.removeValueForKey(key)
    }

    fun clearUserSpecificData() {
        userMMKV.value?.clearAll() // Clears data for the current user
        // Optionally, if you want to delete the mmap file itself:
        // userMMKV.value?.let { MMKV.removeStorage(it.mmapID()) }
    }
}