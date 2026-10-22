package com.ytone.longcare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.di.AppDataStore
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import kotlinx.coroutines.CancellationException
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

internal val APP_USER_KEY = byteArrayPreferencesKey("app_user")
private const val SESSION_READ_RETRY_DELAY_MS = 1_000L
private const val SESSION_PUBLICATION_TIMEOUT_MS = 5_000L

/**
 * UserSessionRepository 的默认实现
 */
@Singleton
class DefaultUserSessionRepository @Inject constructor(
    @param:AppDataStore private val appDataStore: DataStore<Preferences>,
    @param:ApplicationScope private val coroutineScope: CoroutineScope,
    private val faceSession: UserSessionTracker,
) : UserSessionRepository {

    private val mutationMutex = Mutex()
    override val sessionSnapshot = faceSession.sessionSnapshot

    override val sessionState: StateFlow<SessionState> = appDataStore.data
        .retryWhen { exception, _ ->
            if (exception is IOException) {
                // Keep collecting after transient IO failures instead of completing the shared flow.
                // No value means no authorization is available; it must not fabricate a logout.
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
        .onEach { state ->
            faceSession.observe(state.user)
            CrashReportGateway.setUserId(state.user?.userId)
        }
        .stateIn(
            scope = coroutineScope,
            // Session is process-wide state used by receivers and interceptors, not only by UI collectors.
            started = SharingStarted.Eagerly,
            initialValue = SessionState.Unknown
        )

    override suspend fun login(user: User) {
        updateUserInternal(user, forceNewSession = true)
    }

    override suspend fun updateUser(user: User) {
        updateUserInternal(user)
    }

    private suspend fun updateUserInternal(user: User, forceNewSession: Boolean = false) = mutationMutex.withLock {
        val changesSession = forceNewSession || !faceSession.matches(user)
        if (changesSession) faceSession.beginChange(user)
        try {
            appDataStore.edit { preferences ->
                val previousUser = preferences[APP_USER_KEY]?.let { runCatching { User.ADAPTER.decode(it) }.getOrNull() }
                // Token refresh/cosmetic updates for the same account must not erase active order recovery.
                if (forceNewSession || previousUser == null || previousUser.companyId != user.companyId ||
                    previousUser.accountId != user.accountId || previousUser.userId != user.userId) {
                    DataStoreLocationRecoveryStore.clearLocationRecovery(preferences)
                }
                preferences[APP_USER_KEY] = user.encode()
            }
            // Publishing also synchronizes diagnostics and restored session identity.
            awaitSessionState(SessionState.LoggedIn(user))
        } finally {
            if (changesSession) faceSession.finishChange(sessionState.value.user)
        }
    }

    override suspend fun logout() {
        mutationMutex.withLock { logoutLocked() }
    }

    override suspend fun logoutIfCurrent(generation: Long): Long? = mutationMutex.withLock {
        val snapshot = sessionSnapshot.value
        val expectedUser = snapshot.user ?: return@withLock null
        if (snapshot.generation != generation) return@withLock null
        logoutLocked(expectedUser)
    }

    private suspend fun logoutLocked(expectedUser: User? = null): Long? {
        faceSession.beginChange()
        var requestedClear = false
        var cleared = false
        var clearOutcomeUncertain = false
        try {
            appDataStore.edit { preferences ->
                // Recheck durable credentials in the transaction as publication may previously fail.
                if (expectedUser != null) {
                    val durable = preferences[APP_USER_KEY]?.let { runCatching { User.ADAPTER.decode(it) }.getOrNull() }
                    if (durable == null || durable.companyId != expectedUser.companyId ||
                        durable.accountId != expectedUser.accountId || durable.userId != expectedUser.userId ||
                        durable.token != expectedUser.token) return@edit
                }
                DataStoreLocationRecoveryStore.clearLocationRecovery(preferences)
                preferences.remove(APP_USER_KEY)
                requestedClear = true
            }
            // edit returns only after durable commit; a transform followed by IOException aborts.
            cleared = requestedClear
            if (cleared) awaitSessionState(SessionState.LoggedOut)
        } catch (cancelled: CancellationException) {
            // Prompt cancellation may hide a committed write. Do not reopen uncertain credentials.
            clearOutcomeUncertain = requestedClear
            throw cancelled
        } finally {
            // Never restore old authentication after a durable clear whose flow publication failed.
            faceSession.finishChange(if (cleared || clearOutcomeUncertain) null else sessionState.value.user)
        }
        return sessionSnapshot.value.generation.takeIf { cleared }
    }

    private suspend fun awaitSessionState(expected: SessionState) {
        // A persistent read failure must not hold the mutation mutex indefinitely.
        withTimeoutOrNull(SESSION_PUBLICATION_TIMEOUT_MS) {
            sessionState.first { it == expected }
        } ?: throw IOException("Session state could not be published")
    }
}
