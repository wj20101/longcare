package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomUtilsTest {

    @Test
    fun `generateRandomString should return expected length with allowed charset`() {
        val value = RandomUtils.generateRandomString(64)

        assertEquals(64, value.length)
        assertTrue(value.all { it.isLetterOrDigit() })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `generateRandomString should reject non-positive length`() {
        RandomUtils.generateRandomString(0)
    }
}
