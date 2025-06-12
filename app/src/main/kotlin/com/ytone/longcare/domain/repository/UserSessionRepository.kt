package com.ytone.longcare.domain.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ytone.longcare.data.storage.DataStoreKeys
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
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
     * 登录
     */
    fun loginUser(user: User)

    /**
     * 更新user
     */
    fun updateUser(user: User)

    /**
     * 退出登录
     */
    fun logoutUser()
}

/**
 * Default implementation of [UserSessionRepository].
 */
@Singleton
class DefaultUserSessionRepository @Inject constructor(
    @AppDataStore private val appDataStore: DataStore<Preferences>,
    @ApplicationScope private val coroutineScope: CoroutineScope
) : UserSessionRepository {

    private val _currentUser = MutableStateFlow<User?>(null)
    override val currentUser: StateFlow<User?> = _currentUser

    override fun loginUser(user: User) {
        _currentUser.value = user
        coroutineScope.launch {
            appDataStore.edit {
                it[DataStoreKeys.APP_USER] = user.encode()
            }
        }
    }

    override fun updateUser(user: User) {
        coroutineScope.launch {
            appDataStore.edit {
                it[DataStoreKeys.APP_USER] = user.encode()
            }
        }
    }

    override fun logoutUser() {
        _currentUser.value = null
        coroutineScope.launch {
            appDataStore.edit {
                it.remove(DataStoreKeys.APP_USER)
            }
        }
    }
}