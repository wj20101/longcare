package com.ytone.longcare.domain.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.common.utils.logE
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
 * 定义会话状态的密封类。
 * 通过一个抽象属性 `user` 提供对用户对象的便捷访问。
 */
sealed class SessionState {
    /**
     * 当前的用户对象。
     * 仅在 [LoggedIn] 状态下为非空，在 [Unknown] 和 [LoggedOut] 状态下为 null。
     */
    abstract val user: User?

    /**
     * 初始状态，正在从持久化存储中读取用户状态。
     */
    data object Unknown : SessionState() {
        override val user: User? = null
    }

    /**
     * 用户已登录状态，包含用户信息。
     * @param user 当前登录的 User 对象，不能为空。
     */
    data class LoggedIn(override val user: User) : SessionState()

    /**
     * 用户已登出状态。
     */
    data object LoggedOut : SessionState() {
        override val user: User? = null
    }
}


/**
 * 用户会话管理的接口
 */
interface UserSessionRepository {
    /**
     * 一个 [StateFlow]，发射当前的会话状态 [SessionState]。
     */
    val sessionState: StateFlow<SessionState>

    /**
     * 登录用户，并持久化用户信息。
     */
    fun loginUser(user: User)

    /**
     * 更新当前登录的用户信息。
     */
    fun updateUser(user: User)

    /**
     * 退出登录，并清除持久化的用户信息。
     */
    fun logoutUser()
}

/**
 * UserSessionRepository 的默认实现
 */
@Singleton
class DefaultUserSessionRepository @Inject constructor(
    @AppDataStore private val appDataStore: DataStore<Preferences>,
    @ApplicationScope private val coroutineScope: CoroutineScope
) : UserSessionRepository {

    override val sessionState: StateFlow<SessionState> = appDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                // 如果读取DataStore时发生IO异常，视为登出状态
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val userBytes = preferences[DataStoreKeys.APP_USER]
            if (userBytes != null) {
                try {
                    // 解码成功，返回登录状态
                    SessionState.LoggedIn(User.ADAPTER.decode(userBytes))
                } catch (e: IOException) {
                    logE(message = "User data corrupted", throwable = e)
                    // 如果数据损坏导致解码失败，视为登出状态
                    SessionState.LoggedOut
                }
            } else {
                // 如果没有用户数据，视为登出状态
//                SessionState.LoggedOut
                //TODO mock
                SessionState.LoggedIn(User())
            }
        }
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = SessionState.Unknown // <-- 关键：初始状态为 Unknown
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