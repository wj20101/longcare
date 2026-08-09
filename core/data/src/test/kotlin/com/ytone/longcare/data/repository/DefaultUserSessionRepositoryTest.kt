package com.ytone.longcare.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.model.User
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
        assertEquals(
            SessionState.LoggedIn(user),
            repository.sessionState.first { it !is SessionState.Unknown },
        )

        repository.logout()
        assertEquals(
            SessionState.LoggedOut,
            repository.sessionState.first { it is SessionState.LoggedOut },
        )
    }
}
