package com.ytone.longcare.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.model.User
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocationRecoveryStoreTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val user = User(companyId = 1, accountId = 2, userId = 3)
    private val record = LocationRecoveryRecord(LocationAccount(1, 2, 3), 10, "session-10")

    @Test fun `only identity persists and account switch rejects late writes`() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.root.resolve("location.preferences_pb")
        }
        val recovery = DataStoreLocationRecoveryStore(data)
        assertNull(recovery.read())
        recovery.save(record)
        assertNull(recovery.read())
        val sessions = DefaultUserSessionRepository(data, backgroundScope, UserSessionTracker())
        sessions.login(user)
        recovery.save(record)
        assertEquals(record, recovery.read())
        sessions.updateUser(user.copy(userName = "cosmetic update"))
        assertEquals(record, recovery.read())
        sessions.updateUser(user.copy(token = "rotated credential"))
        assertEquals("Same account credential refresh must retain recovery", record, recovery.read())
        sessions.login(user.copy(accountId = 4))
        assertNull(recovery.read())
        recovery.save(record)
        assertNull("An old account write must not restore tracking", recovery.read())
    }

    @Test fun `logout clears recovery atomically and old owner cannot clear new owner`() = runTest {
        val data = PreferenceDataStoreFactory.create(scope = backgroundScope) {
            temporaryFolder.root.resolve("logout.preferences_pb")
        }
        data.edit { it[byteArrayPreferencesKey("app_user")] = user.encode() }
        val recovery = DataStoreLocationRecoveryStore(data)
        recovery.save(record)
        val newer = record.copy(orderId = 11, sessionId = "session-11")
        recovery.save(newer)
        recovery.clear(record.sessionId)
        assertEquals(newer, recovery.read())
        DefaultUserSessionRepository(data, backgroundScope, UserSessionTracker()).logout()
        assertNull(recovery.read())
        recovery.save(newer)
        assertNull(recovery.read())
    }
}
