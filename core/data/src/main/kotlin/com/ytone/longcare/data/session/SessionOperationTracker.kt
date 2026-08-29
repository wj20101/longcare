package com.ytone.longcare.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Registers caller-owned suspending operations under a session identity so logout can revoke
 * and join them before the next user namespace is opened.
 */
internal class SessionOperationTracker {
    private val mutex = Mutex()
    private val jobsBySession = mutableMapOf<String, MutableSet<Job>>()

    suspend fun <T> track(
        sessionFingerprint: String,
        validateSession: () -> Unit,
        operation: suspend () -> T,
    ): T {
        val job = checkNotNull(currentCoroutineContext()[Job]) {
            "A session operation requires a coroutine Job"
        }
        mutex.withLock {
            validateSession()
            jobsBySession.getOrPut(sessionFingerprint, ::linkedSetOf).add(job)
        }
        return try {
            operation()
        } finally {
            withContext(NonCancellable) {
                mutex.withLock {
                    jobsBySession[sessionFingerprint]?.let { jobs ->
                        jobs.remove(job)
                        if (jobs.isEmpty()) jobsBySession.remove(sessionFingerprint)
                    }
                }
            }
        }
    }

    suspend fun cancelAndJoin(sessionFingerprint: String) = withContext(NonCancellable) {
        val cleanupJob = currentCoroutineContext()[Job]
        val jobs = mutex.withLock {
            jobsBySession.remove(sessionFingerprint)
                .orEmpty()
                .filterNot { it === cleanupJob }
        }
        jobs.forEach { job ->
            job.cancel(CancellationException("Operation belongs to a revoked user session"))
        }
        jobs.joinAll()
    }

    internal suspend fun activeCount(sessionFingerprint: String): Int = mutex.withLock {
        jobsBySession[sessionFingerprint]?.size ?: 0
    }
}
