package com.ytone.longcare.domain.repository

import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interface for managing user session data, primarily the current user's ID.
 */
interface UserSessionRepository {
    /**
     * A [StateFlow] emitting the current user. Emits null if no user is logged in.
     */
    val currentUser: StateFlow<User?>

    /**
     * Call when a user logs in.
     * @param user The logged-in user.
     */
    fun loginUser(user: User)

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
    private val _currentUser = MutableStateFlow<User?>(null) // Initially no user
    override val currentUser: StateFlow<User?> = _currentUser

    override fun loginUser(user: User) {
        _currentUser.value = user
    }

    override fun logoutUser() {
        _currentUser.value = null
    }
}