package com.ytone.longcare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val APP_USER_KEY = byteArrayPreferencesKey("app_user")
private const val SESSION_READ_RETRY_DELAY_MS = 1_000L
private const val SESSION_PUBLICATION_TIMEOUT_MS = 5_000L

/**
 * UserSessionRepository 的默认实现
 */
@Singleton
class DefaultUserSessionRepository @Inject constructor(
    @param:AppDataStore private val appDataStore: DataStore<Preferences>,
    @param:ApplicationScope private val coroutineScope: CoroutineScope
) : UserSessionRepository {

    private val mutationMutex = Mutex()

    override val sessionState: StateFlow<SessionState> = appDataStore.data
        .retryWhen { exception, _ ->
            if (exception is IOException) {
                // Keep collecting after transient IO failures instead of completing the shared flow.
                emit(emptyPreferences())
                delay(SESSION_READ_RETRY_DELAY_MS)
                true
            } else {
                false
            }
        }
        .map { preferences ->
            val userBytes = preferences[APP_USER_KEY]
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
        .onEach { state -> CrashReportGateway.setUserId(state.user?.userId) }
        .stateIn(
            scope = coroutineScope,
            // Session is process-wide state used by receivers and interceptors, not only by UI collectors.
            started = SharingStarted.Eagerly,
            initialValue = SessionState.Unknown
        )

    override suspend fun login(user: User) {
        updateUserInternal(user)
    }

    override suspend fun updateUser(user: User) {
        updateUserInternal(user)
    }

    private suspend fun updateUserInternal(user: User) = mutationMutex.withLock {
        appDataStore.edit { preferences ->
            preferences[APP_USER_KEY] = user.encode()
        }
        // Publishing the state also synchronizes diagnostics. Callers can report immediately.
        awaitSessionState(SessionState.LoggedIn(user))
    }

    override suspend fun logout() = mutationMutex.withLock {
        appDataStore.edit { preferences ->
            preferences.remove(APP_USER_KEY)
        }
        awaitSessionState(SessionState.LoggedOut)
    }

    private suspend fun awaitSessionState(expected: SessionState) {
        // A persistent read failure must not hold the mutation mutex indefinitely.
        withTimeoutOrNull(SESSION_PUBLICATION_TIMEOUT_MS) {
            sessionState.first { it == expected }
        } ?: throw IOException("Session state could not be published")
    }
}
