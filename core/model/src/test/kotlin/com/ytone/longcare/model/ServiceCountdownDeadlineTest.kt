package com.ytone.longcare.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceCountdownDeadlineTest {
    private val start = ServiceTimeSnapshot(1_000_000L, 200_000L, 7)
    private val duration = 15 * 60_000L
    private val deadline = ServiceCountdownDeadline(
        wallTimeMillis = start.wallTimeMillis + duration,
        elapsedRealtimeMillis = start.elapsedRealtimeMillis + duration,
        bootCount = start.bootCount,
    )

    @Test
    fun `same boot wall corrections only change the RTC projection`() {
        val originalIdentity = deadline.wallTimeMillis
        val afterFiveMinutes = ServiceTimeSnapshot(start.wallTimeMillis + 300_000L, start.elapsedRealtimeMillis + 300_000L, 7)

        for (clockShift in listOf(-300_000L, 0L, 300_000L)) {
            val now = afterFiveMinutes.copy(wallTimeMillis = afterFiveMinutes.wallTimeMillis + clockShift)
            assertTrue(deadline.usesMonotonicTime(now))
            assertEquals(600_000L, deadline.remainingMillis(now))
            assertEquals(originalIdentity + clockShift, deadline.triggerAtMillis(now))
            assertEquals(originalIdentity, deadline.wallTimeMillis)
        }
    }

    @Test
    fun `elapsed time through deep sleep reaches and passes the deadline without display ticks`() {
        // No intervening timer callback is needed. elapsedRealtime includes the time spent asleep.
        val beforeDue = ServiceTimeSnapshot(start.wallTimeMillis + duration - 1L, start.elapsedRealtimeMillis + duration - 1L, 7)
        val atDue = beforeDue.copy(wallTimeMillis = beforeDue.wallTimeMillis + 1L, elapsedRealtimeMillis = beforeDue.elapsedRealtimeMillis + 1L)
        val afterSleep = atDue.copy(wallTimeMillis = atDue.wallTimeMillis + 30_000L, elapsedRealtimeMillis = atDue.elapsedRealtimeMillis + 30_000L)

        assertEquals(1L, deadline.remainingMillis(beforeDue))
        assertEquals(0L, deadline.remainingMillis(atDue))
        assertEquals(-30_000L, deadline.remainingMillis(afterSleep))
        assertEquals(deadline.wallTimeMillis, deadline.triggerAtMillis(afterSleep))
    }

    @Test
    fun `different boot falls back to the original wall deadline without reusing reset elapsed time`() {
        val afterReboot = ServiceTimeSnapshot(start.wallTimeMillis + 600_000L, 5_000L, 8)

        assertFalse(deadline.usesMonotonicTime(afterReboot))
        assertEquals(300_000L, deadline.remainingMillis(afterReboot))
        assertEquals(deadline.wallTimeMillis, deadline.triggerAtMillis(afterReboot))
    }

    @Test
    fun `unknown or invalid boot identity never proves monotonic compatibility`() {
        val now = ServiceTimeSnapshot(start.wallTimeMillis + 600_000L, start.elapsedRealtimeMillis + 100_000L, 7)
        val cases = listOf(
            deadline.copy(bootCount = null) to now,
            deadline to now.copy(bootCount = null),
            deadline.copy(bootCount = null) to now.copy(bootCount = null),
            deadline.copy(bootCount = -1) to now.copy(bootCount = -1),
            deadline to now.copy(bootCount = -1),
            deadline.copy(bootCount = -1) to now,
        )

        for ((stored, current) in cases) {
            assertFalse(stored.usesMonotonicTime(current))
            assertEquals(300_000L, stored.remainingMillis(current))
            assertEquals(deadline.wallTimeMillis, stored.triggerAtMillis(current))
            assertEquals(deadline.wallTimeMillis, stored.wallTimeMillis)
        }
    }

    @Test
    fun `legacy wall origin stays a fallback even when the current boot is known`() {
        val legacy = ServiceCountdownDeadline(deadline.wallTimeMillis)
        val now = ServiceTimeSnapshot(start.wallTimeMillis + 600_000L, 1L, 7)

        assertFalse(legacy.usesMonotonicTime(now))
        assertEquals(300_000L, legacy.remainingMillis(now))
        assertEquals(deadline.wallTimeMillis, legacy.triggerAtMillis(now))
    }
}
