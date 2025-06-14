package com.ytone.longcare.domain.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.data.storage.DataStoreKeys
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
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
                SessionState.LoggedOut
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