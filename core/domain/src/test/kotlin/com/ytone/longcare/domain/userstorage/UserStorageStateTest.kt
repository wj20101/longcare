package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.UserScopeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserStorageStateTest {
    private val lease = UserStorageLease(
        scopeKey = UserScopeKey(1, 2, 3),
        sessionEpoch = SessionEpoch(10),
        generation = StorageGeneration(20),
    )

    @Test
    fun `legal lifecycle transitions are explicit`() {
        val opening = UserStorageState.Opening(lease)
        val ready = UserStorageState.Ready(lease)
        val closing = UserStorageState.Closing(lease)

        assertTrue(UserStorageTransitionPolicy.canTransition(UserStorageState.LoggedOut, opening))
        assertTrue(UserStorageTransitionPolicy.canTransition(opening, ready))
        assertTrue(UserStorageTransitionPolicy.canTransition(ready, closing))
        assertTrue(UserStorageTransitionPolicy.canTransition(closing, UserStorageState.LoggedOut))
        assertFalse(UserStorageTransitionPolicy.canTransition(UserStorageState.LoggedOut, ready))
        assertFalse(UserStorageTransitionPolicy.canTransition(ready, opening))
    }

    @Test
    fun `expired generation is rejected`() {
        val stale = lease.copy(generation = StorageGeneration(19))

        assertEquals(
            LeaseValidation.Rejected(LeaseRejection.GENERATION_EXPIRED),
            stale.validateAgainst(UserStorageState.Ready(lease)),
        )
    }

    @Test
    fun `matching ready lease is valid`() {
        assertEquals(LeaseValidation.Valid, lease.validateAgainst(UserStorageState.Ready(lease)))
    }
}
