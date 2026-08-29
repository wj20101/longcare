package com.ytone.longcare.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionOperationTrackerTest {
    @Test
    fun `revoking one session cancels and joins only that sessions operations`() = runTest {
        val tracker = SessionOperationTracker()
        val otherMayFinish = CompletableDeferred<Unit>()
        val sessionA = async {
            tracker.track("A:1", validateSession = {}) { awaitCancellation() }
        }
        val sessionB = async {
            tracker.track("B:2", validateSession = {}) {
                otherMayFinish.await()
                "B-result"
            }
        }
        runCurrent()
        assertEquals(1, tracker.activeCount("A:1"))
        assertEquals(1, tracker.activeCount("B:2"))

        tracker.cancelAndJoin("A:1")

        assertTrue(sessionA.isCancelled)
        assertEquals(0, tracker.activeCount("A:1"))
        assertTrue(sessionB.isActive)
        otherMayFinish.complete(Unit)
        assertEquals("B-result", sessionB.await())
    }

    @Test
    fun `registration validates session before an operation can start`() = runTest {
        val tracker = SessionOperationTracker()
        var started = false

        val failure = runCatching {
            tracker.track(
                sessionFingerprint = "expired",
                validateSession = { throw CancellationException("expired") },
            ) {
                started = true
            }
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(!started)
        assertEquals(0, tracker.activeCount("expired"))
    }
}
