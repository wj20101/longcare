package com.ytone.longcare.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CurrentUserTest {
    @Test
    fun `public current user contains no session secrets`() {
        val source = User(
            companyId = 1,
            accountId = 2,
            userId = 3,
            userName = "护理员",
            headUrl = "avatar",
            userIdentity = 1,
            identityCardNumber = "secret-id-card",
            gender = 2,
            token = "secret-token",
        )

        val current = source.toCurrentUser()
        val fields = CurrentUser::class.java.declaredFields.map { it.name }.toSet()

        assertEquals(3, current.userId)
        assertEquals("护理员", current.userName)
        assertFalse("token" in fields)
        assertFalse("identityCardNumber" in fields)
        assertFalse(current.toString().contains("secret-token"))
        assertFalse(current.toString().contains("secret-id-card"))
    }
}
