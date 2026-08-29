package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.UserScopeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class UserTaskIdentityTest {
    private val namespace = UserScopeKey(1, 2, 3).namespaceId()

    @Test
    fun `encoding is deterministic and contains no session secret field`() {
        val identity = UserTaskIdentity(namespace, SessionEpoch(4), "service-reminder", "order-5")

        assertEquals(identity.encode(), identity.copy().encode())
        assertFalse(identity.encode().contains("token"))
        assertFalse(UserTaskIdentity::class.java.declaredFields.any { it.name == "token" })
    }

    @Test
    fun `each identity component changes encoding`() {
        val base = UserTaskIdentity(namespace, SessionEpoch(4), "service-reminder", "order-5")

        assertNotEquals(
            base.encode(),
            base.copy(namespaceId = UserScopeKey(9, 2, 3).namespaceId()).encode(),
        )
        assertNotEquals(base.encode(), base.copy(sessionEpoch = SessionEpoch(5)).encode())
        assertNotEquals(base.encode(), base.copy(taskType = "countdown").encode())
        assertNotEquals(base.encode(), base.copy(businessId = "order-6").encode())
    }
}
