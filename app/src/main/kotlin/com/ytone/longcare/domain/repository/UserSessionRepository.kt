package com.ytone.longcare.domain.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for managing user session data, primarily the current user's ID.
 */
interface UserSessionRepository {
    /**
     * A [StateFlow] emitting the current user's ID. Emits null if no user is logged in.
     */
    val currentUserId: StateFlow<String?>

    /**
     * Call when a user logs in.
     * @param userId The ID of the logged-in user.
     */
    fun loginUser(userId: String)

    /**
     * Call when a user logs out.
     */
    fun logoutUser()
}

/**
 * Default implementation of [UserSessionRepository].
 */
@Singleton
class DefaultUserSessionRepository @Inject constructor() : UserSessionRepository {
    private val _currentUserId = MutableStateFlow<String?>(null) // Initially no user
    override val currentUserId: StateFlow<String?> = _currentUserId

    override fun loginUser(userId: String) {
        _currentUserId.value = userId
    }

    override fun logoutUser() {
        _currentUserId.value = null
    }
}