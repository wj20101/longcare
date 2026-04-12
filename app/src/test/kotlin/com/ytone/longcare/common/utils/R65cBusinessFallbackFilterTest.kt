package com.ytone.longcare.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class R65cBusinessFallbackFilterTest {

    @Test
    fun `accepts uppercase hex tag ids that match NFC-style format`() {
        val filter = R65cBusinessFallbackFilter()

        val result = filter.consume(rawPayload = " 0426fafa051f91 ")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), result)
    }

    @Test
    fun `rejects payload containing non hex characters`() {
        val filter = R65cBusinessFallbackFilter()

        val result = filter.consume(rawPayload = "901948不EA8想0想")

        assertEquals(R65cBusinessFallbackResult.Invalid(1), result)
    }

    @Test
    fun `suppresses duplicate valid payload inside duplicate window`() {
        val filter = R65cBusinessFallbackFilter({ 1000L }, 1500L, 3)

        val first = filter.consume(rawPayload = "0426FAFA051F91")
        val second = filter.consume(rawPayload = "0426FAFA051F91")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), first)
        assertEquals(R65cBusinessFallbackResult.DuplicateSuppressed("0426FAFA051F91"), second)
    }

    @Test
    fun `returns escalated invalid state after threshold`() {
        val filter = R65cBusinessFallbackFilter({ 0L }, 1500L, 3)

        assertEquals(R65cBusinessFallbackResult.Invalid(1), filter.consume("中文"))
        assertEquals(R65cBusinessFallbackResult.Invalid(2), filter.consume("abc-123"))
        assertEquals(R65cBusinessFallbackResult.DeviceError(streak = 3), filter.consume("123456789"))
    }

    @Test
    fun `valid payload resets invalid streak`() {
        val filter = R65cBusinessFallbackFilter({ 0L }, 1500L, 3)

        filter.consume("中文")
        val valid = filter.consume("0426FAFA051F91")
        val nextInvalid = filter.consume("中文")

        assertEquals(R65cBusinessFallbackResult.Valid("0426FAFA051F91"), valid)
        assertEquals(R65cBusinessFallbackResult.Invalid(1), nextInvalid)
    }
}
