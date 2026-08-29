package com.ytone.longcare.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserScopeKeyTest {
    private val scope = UserScopeKey(companyId = 11, accountId = 22, userId = 33)

    @Test
    fun `namespace is stable and does not reveal raw ids`() {
        val first = scope.namespaceId().value
        val second = UserScopeKey(11, 22, 33).namespaceId().value

        assertEquals(first, second)
        assertTrue(first.matches(Regex("v1_[0-9a-f]{64}")))
        assertFalse(first.contains("11"))
        assertFalse(first.contains("22"))
        assertFalse(first.contains("33"))
        assertFalse(scope.toString().contains("companyId"))
    }

    @Test
    fun `every ownership component changes namespace`() {
        assertNotEquals(scope.namespaceId(), UserScopeKey(12, 22, 33).namespaceId())
        assertNotEquals(scope.namespaceId(), UserScopeKey(11, 23, 33).namespaceId())
        assertNotEquals(scope.namespaceId(), UserScopeKey(11, 22, 34).namespaceId())
    }

    @Test
    fun `token and presentation changes do not affect namespace`() {
        val first = User(
            companyId = 11,
            accountId = 22,
            userId = 33,
            userName = "A",
            token = "token-a",
        )
        val second = first.copy(userName = "B", token = "token-b", headUrl = "new")

        assertEquals(first.requireScopeKey().namespaceId(), second.requireScopeKey().namespaceId())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `incomplete identity is rejected`() {
        UserScopeKey(companyId = 0, accountId = 22, userId = 33)
    }
}
