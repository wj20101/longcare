package com.ytone.longcare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.ytone.longcare.model.User
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class UserSessionLogoutFailureTest {
    @Test fun `aborted durable clear retains authentication so a new request can retry invalidation`() = runTest {
        val values = MutableStateFlow(emptyPreferences())
        var failWrite = false
        val store = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                val next = transform(values.value)
                if (failWrite) throw IOException("test disk write failure")
                values.value = next
                return next
            }
        }
        val repository = DefaultUserSessionRepository(store, backgroundScope, UserSessionTracker())
        val user = User(userId = 1)
        repository.login(user)
        failWrite = true
        try {
            repository.logoutIfCurrent(repository.sessionSnapshot.value.generation)
            fail("Aborted transaction must not claim logout completion")
        } catch (_: IOException) {
            assertEquals(user, User.ADAPTER.decode(requireNotNull(values.value[APP_USER_KEY])))
            assertEquals(user, repository.sessionSnapshot.value.user)
        }
        failWrite = false
        assertNotNull(repository.logoutIfCurrent(repository.sessionSnapshot.value.generation))
        assertNull(repository.sessionSnapshot.value.user)
        assertNull(values.value[APP_USER_KEY])
    }

    @Test fun `cancellation with an uncertain clear outcome never reopens old authentication`() = runTest {
        val values = MutableStateFlow(emptyPreferences())
        var cancelWrite = false
        val store = object : DataStore<Preferences> {
            override val data = values
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                val next = transform(values.value)
                if (cancelWrite) throw CancellationException("test uncertain commit")
                values.value = next
                return next
            }
        }
        val repository = DefaultUserSessionRepository(store, backgroundScope, UserSessionTracker())
        repository.login(User(userId = 1))
        cancelWrite = true
        try {
            repository.logoutIfCurrent(repository.sessionSnapshot.value.generation)
            fail("Cancellation must propagate instead of returning a completion stamp")
        } catch (_: CancellationException) {
            assertNull(repository.sessionSnapshot.value.user)
        }
    }

}
