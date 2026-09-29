package com.ytone.longcare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.model.User
import java.io.IOException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DefaultUserSessionRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `login and logout complete only after durable session update`() = runTest {
        val dataStore =
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                temporaryFolder.root.resolve("session.preferences_pb")
            }
        val repository = DefaultUserSessionRepository(dataStore, backgroundScope)
        val user = User(userId = 7, token = "token")

        repository.login(user)
        assertEquals("7", CrashReportGateway.userId)
        assertEquals(
            SessionState.LoggedIn(user),
            repository.sessionState.first { it !is SessionState.Unknown },
        )

        repository.logout()
        assertEquals("0", CrashReportGateway.userId)
        assertEquals(
            SessionState.LoggedOut,
            repository.sessionState.first { it is SessionState.LoggedOut },
        )
    }

    @Test
    fun `persisted identity is restored without a UI collector and account changes stay unmodified`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.root.resolve("restored.preferences_pb")
        }
        dataStore.edit { it[byteArrayPreferencesKey("app_user")] = User(userId = 123).encode() }
        val repository = DefaultUserSessionRepository(dataStore, backgroundScope)
        repository.sessionState.first { it is SessionState.LoggedIn }
        assertEquals("123", CrashReportGateway.userId)
        repository.login(User(userId = 456))
        assertEquals("456", CrashReportGateway.userId)
        repository.updateUser(User(userId = 456, userName = "Updated"))
        assertEquals("456", CrashReportGateway.userId)
        repository.logout()
        assertEquals("0", CrashReportGateway.userId)
    }

    @Test
    fun `login recovers after a transient read failure and subsequent mutations complete`() = runTest {
        val dataStore = FailingReadDataStore(failuresRemaining = 1)
        val repository = DefaultUserSessionRepository(dataStore, backgroundScope)
        repository.sessionState.first { it == SessionState.LoggedOut }
        val user = User(userId = 123)

        withTimeout(10_000) { repository.login(user) }
        assertEquals(SessionState.LoggedIn(user), repository.sessionState.value)
        assertEquals("123", CrashReportGateway.userId)
        assertEquals(user, User.ADAPTER.decode(dataStore.data.first()[byteArrayPreferencesKey("app_user")]!!))

        repository.updateUser(user.copy(userName = "Updated"))
        assertEquals("Updated", repository.sessionState.value.user?.userName)
        repository.logout()
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertEquals("0", CrashReportGateway.userId)
    }

    @Test
    fun `persistent read failure bounds publication wait and releases mutation mutex`() = runTest {
        val dataStore = FailingReadDataStore(failuresRemaining = Int.MAX_VALUE)
        val repository = DefaultUserSessionRepository(dataStore, backgroundScope)
        repository.sessionState.first { it == SessionState.LoggedOut }

        withTimeout(10_000) {
            try {
                repository.login(User(userId = 123))
                fail("A failed state publication must be reported to the caller")
            } catch (_: IOException) {
                // The durable write succeeded, but state reads remain unavailable.
                assertEquals(
                    User(userId = 123),
                    User.ADAPTER.decode(dataStore.persisted.value[byteArrayPreferencesKey("app_user")]!!),
                )
                assertEquals(SessionState.LoggedOut, repository.sessionState.value)
            }
        }
        dataStore.failuresRemaining = 0
        withTimeout(10_000) { repository.login(User(userId = 456)) }
        assertEquals("456", CrashReportGateway.userId)
        repository.logout()
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
    }

    @Test
    fun `cancelling a publication wait releases the mutex without stopping read recovery`() = runTest {
        val dataStore = FailingReadDataStore(failuresRemaining = Int.MAX_VALUE)
        val repository = DefaultUserSessionRepository(dataStore, backgroundScope)
        val login = async { repository.login(User(userId = 123)) }
        dataStore.persisted.first { it[byteArrayPreferencesKey("app_user")] != null }
        login.cancelAndJoin()
        assertTrue(login.isCancelled)

        dataStore.failuresRemaining = 0
        withTimeout(10_000) { repository.login(User(userId = 456)) }
        assertEquals("456", CrashReportGateway.userId)
    }

    private class FailingReadDataStore(var failuresRemaining: Int) : DataStore<Preferences> {
        val persisted = MutableStateFlow(emptyPreferences())
        override val data = flow {
            if (failuresRemaining > 0) {
                failuresRemaining--
                throw IOException("Read temporarily unavailable")
            }
            emitAll(persisted)
        }

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            transform(persisted.value).also { persisted.value = it }
    }
}
