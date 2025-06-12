package com.ytone.longcare.domain.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.data.storage.DataStoreKeys
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
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

    override val currentUser: StateFlow<User?> = appDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                // 遇到错误时，发射一个空的 Preferences，让下游可以处理为 null
                emit(emptyPreferences())
            } else {
                // 如果是其他类型的异常，则向上抛出
                throw exception
            }
        }
        .map { preferences ->
            val userBytes = preferences[DataStoreKeys.APP_USER]
            if (userBytes != null) {
                runCatching { User.ADAPTER.decode(userBytes) }.getOrNull()
            } else {
                null // 没有找到用户数据，返回 null
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L), // 推荐
            initialValue = null // 明确初始状态为 null (未登录)
        )

    override fun loginUser(user: User) {
        updateUserInternal(user)
    }

    override fun updateUser(user: User) {
        updateUserInternal(user)
    }

    private fun updateUserInternal(user: User) {
        coroutineScope.launch {
            appDataStore.edit { preferences ->
                preferences[DataStoreKeys.APP_USER] = user.encode()
            }
        }
    }

    override fun logoutUser() {
        coroutineScope.launch {
            appDataStore.edit { preferences ->
                preferences.remove(DataStoreKeys.APP_USER)
            }
        }
    }
}