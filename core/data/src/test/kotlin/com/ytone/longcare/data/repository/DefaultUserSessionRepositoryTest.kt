package com.ytone.longcare.data.repository

import com.ytone.longcare.data.session.SessionEnvelope
import com.ytone.longcare.data.session.SessionEnvelopePersistence
import com.ytone.longcare.data.session.SessionEnvelopePhase
import com.ytone.longcare.data.session.SessionEnvelopeReadResult
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeReadyHook
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultUserSessionRepositoryTest {
    @Test
    fun `process recreation cannot read ACTIVE envelope before cutover marker commits`() = runTest {
        val envelope = SessionEnvelope(SessionEnvelopePhase.ACTIVE, SessionEpoch(76), payload())
        val gate = BlockingCutoverGate()
        var persistenceReads = 0
        val persistence = object : SessionEnvelopePersistence {
            override suspend fun read(): SessionEnvelopeReadResult {
                persistenceReads += 1
                return SessionEnvelopeReadResult.Loaded(envelope)
            }

            override suspend fun write(envelope: SessionEnvelope) = Unit
            override suspend fun clear() = Unit
        }
        val runtime = FakeStorageRuntime()
        val repository = DefaultUserSessionRepository(
            persistence = persistence,
            storageRuntime = runtime,
            applicationScope = backgroundScope,
            epochSource = FakeEpochSource(1),
            cutoverGate = gate,
        )
        runCurrent()
        gate.started.await()

        assertEquals(SessionState.Unknown, repository.sessionState.value)
        assertEquals(0, persistenceReads)
        assertTrue(runtime.events.isEmpty())
        assertNull(repository.requestAuthSnapshot())

        gate.release.complete(Unit)

        assertEquals(
            SessionState.LoggedIn(payload().toCurrentUser()),
            repository.sessionState.first { it !is SessionState.Unknown },
        )
        assertEquals(1, persistenceReads)
        assertEquals(listOf("open:76"), runtime.events)
    }

    @Test
    fun `login publishes only after pending close open and active commit`() = runTest {
        val events = mutableListOf<String>()
        val persistence = FakePersistence(SessionEnvelopeReadResult.Missing, events)
        val runtime = FakeStorageRuntime(events)
        val repository = DefaultUserSessionRepository(
            persistence,
            runtime,
            backgroundScope,
            FakeEpochSource(100),
        )
        repository.sessionState.first { it is SessionState.LoggedOut }

        repository.login(payload())

        assertEquals(
            listOf("write:PENDING", "revoke", "close", "open:101", "write:ACTIVE"),
            events,
        )
        assertEquals(SessionState.LoggedIn(payload().toCurrentUser()), repository.sessionState.value)
        assertEquals("secret-token", repository.requestAuthSnapshot()?.token)
        assertEquals("330000199901011234", repository.faceSetupIdentity()?.identityCardNumber)
        val publicFields = repository.sessionState.value.user!!::class.java.declaredFields.map { it.name }
        assertFalse("token" in publicFields)
        assertFalse("identityCardNumber" in publicFields)

        repository.logout()
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertNull(repository.requestAuthSnapshot())
        assertTrue(persistence.cleared)
    }

    @Test
    fun `storage open failure clears pending envelope and remains logged out`() = runTest {
        val persistence = FakePersistence(SessionEnvelopeReadResult.Missing)
        val runtime = FakeStorageRuntime().apply { openFailure = IllegalStateException("metadata mismatch") }
        val repository = DefaultUserSessionRepository(
            persistence,
            runtime,
            backgroundScope,
            FakeEpochSource(200),
        )
        repository.sessionState.first { it is SessionState.LoggedOut }

        val error = runCatching { repository.login(payload()) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertTrue(persistence.cleared)
        assertNull(repository.requestAuthSnapshot())
    }

    @Test
    fun `active envelope restores storage before public login while pending rolls back`() = runTest {
        val activeEnvelope = SessionEnvelope(SessionEnvelopePhase.ACTIVE, SessionEpoch(77), payload())
        val activeRuntime = FakeStorageRuntime()
        val activeRepository = DefaultUserSessionRepository(
            FakePersistence(SessionEnvelopeReadResult.Loaded(activeEnvelope)),
            activeRuntime,
            backgroundScope,
            FakeEpochSource(1),
        )

        assertEquals(
            SessionState.LoggedIn(payload().toCurrentUser()),
            activeRepository.sessionState.first { it !is SessionState.Unknown },
        )
        assertEquals(listOf("open:77"), activeRuntime.events)

        val pendingPersistence = FakePersistence(
            SessionEnvelopeReadResult.Loaded(activeEnvelope.copy(phase = SessionEnvelopePhase.PENDING))
        )
        val pendingRuntime = FakeStorageRuntime()
        val pendingRepository = DefaultUserSessionRepository(
            pendingPersistence,
            pendingRuntime,
            backgroundScope,
            FakeEpochSource(1),
        )
        assertEquals(
            SessionState.LoggedOut,
            pendingRepository.sessionState.first { it !is SessionState.Unknown },
        )
        assertEquals(listOf("revoke", "close"), pendingRuntime.events)
        assertTrue(pendingPersistence.cleared)
    }

    @Test
    fun `account switch finishes old runtime cleanup before new storage becomes ready`() = runTest {
        val events = mutableListOf<String>()
        val repository = DefaultUserSessionRepository(
            FakePersistence(SessionEnvelopeReadResult.Missing, events),
            FakeStorageRuntime(events),
            backgroundScope,
            FakeEpochSource(300),
            cleanupHooks = {
                setOf(
                    SessionRuntimeCleanupHook { identity ->
                        events += "cleanup:${identity.sessionEpoch.value}"
                    }
                )
            },
        )
        repository.sessionState.first { it is SessionState.LoggedOut }
        repository.login(payload())
        events.clear()

        repository.login(payload().copy(accountId = 9, userId = 10))

        assertEquals(
            listOf("write:PENDING", "revoke", "cleanup:301", "close", "open:302", "write:ACTIVE"),
            events,
        )
    }

    @Test
    fun `account switch cancels and joins post login work before opening B`() = runTest {
        val events = mutableListOf<String>()
        val readyStarted = CompletableDeferred<Unit>()
        val repository = DefaultUserSessionRepository(
            persistence = FakePersistence(SessionEnvelopeReadResult.Missing, events),
            storageRuntime = FakeStorageRuntime(events),
            applicationScope = backgroundScope,
            epochSource = FakeEpochSource(700),
            cleanupHooks = {
                setOf(
                    SessionRuntimeCleanupHook { identity ->
                        events += "cleanup:${identity.sessionEpoch.value}"
                    }
                )
            },
            readyHooks = {
                setOf(
                    SessionRuntimeReadyHook { identity, _ ->
                        events += "ready:${identity.sessionEpoch.value}"
                        readyStarted.complete(Unit)
                        try {
                            awaitCancellation()
                        } finally {
                            events += "ready-stop:${identity.sessionEpoch.value}"
                        }
                    }
                )
            },
        )
        repository.sessionState.first { it is SessionState.LoggedOut }
        repository.login(payload())
        runCurrent()
        readyStarted.await()
        events.clear()

        repository.login(payload().copy(accountId = 9, userId = 10))

        assertTrue(events.indexOf("ready-stop:701") in 0 until events.indexOf("open:702"))
        assertTrue(events.indexOf("cleanup:701") in 0 until events.indexOf("open:702"))
        assertEquals(SessionState.LoggedIn(payload().copy(accountId = 9, userId = 10).toCurrentUser()), repository.sessionState.value)
    }

    @Test
    fun `cleanup cancellation is rethrown after logout still clears durable session`() = runTest {
        val persistence = FakePersistence(SessionEnvelopeReadResult.Missing)
        val repository = DefaultUserSessionRepository(
            persistence,
            FakeStorageRuntime(),
            backgroundScope,
            FakeEpochSource(400),
            cleanupHooks = {
                setOf(SessionRuntimeCleanupHook { throw CancellationException("cleanup cancelled") })
            },
        )
        repository.sessionState.first { it is SessionState.LoggedOut }
        repository.login(payload())

        val failure = runCatching { repository.logout() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(persistence.cleared)
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertNull(repository.requestAuthSnapshot())
    }

    @Test
    fun `account switch cleanup cancellation prevents new namespace and fails closed`() = runTest {
        val persistence = FakePersistence(SessionEnvelopeReadResult.Missing)
        val runtime = FakeStorageRuntime()
        var cleanupCalls = 0
        val repository = DefaultUserSessionRepository(
            persistence,
            runtime,
            backgroundScope,
            FakeEpochSource(500),
            cleanupHooks = {
                setOf(
                    SessionRuntimeCleanupHook { identity ->
                        if (identity.sessionEpoch == SessionEpoch(501)) {
                            cleanupCalls += 1
                            throw CancellationException("old runtime cancellation")
                        }
                    }
                )
            },
        )
        repository.sessionState.first { it is SessionState.LoggedOut }
        repository.login(payload())
        runtime.events.clear()

        val failure = runCatching {
            repository.login(payload().copy(accountId = 9, userId = 10))
        }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(cleanupCalls >= 1)
        assertFalse("open:502" in runtime.events)
        assertTrue(persistence.cleared)
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertNull(repository.requestAuthSnapshot())
    }

    @Test
    fun `active restore metadata failure closes storage clears envelope and logs out`() = runTest {
        val activeEnvelope = SessionEnvelope(SessionEnvelopePhase.ACTIVE, SessionEpoch(88), payload())
        val persistence = FakePersistence(SessionEnvelopeReadResult.Loaded(activeEnvelope))
        val runtime = FakeStorageRuntime().apply {
            openFailure = IllegalStateException("namespace metadata mismatch")
        }

        val repository = DefaultUserSessionRepository(
            persistence,
            runtime,
            backgroundScope,
            FakeEpochSource(1),
        )

        assertEquals(
            SessionState.LoggedOut,
            repository.sessionState.first { it !is SessionState.Unknown },
        )
        assertEquals(listOf("open:88", "revoke", "close"), runtime.events)
        assertTrue(persistence.cleared)
        assertNull(repository.requestAuthSnapshot())
    }

    @Test
    fun `uncertain pending write failure revokes previous session instead of restoring it`() = runTest {
        val persistence = FakePersistence(SessionEnvelopeReadResult.Missing)
        val runtime = FakeStorageRuntime()
        val repository = DefaultUserSessionRepository(
            persistence,
            runtime,
            backgroundScope,
            FakeEpochSource(600),
        )
        repository.sessionState.first { it is SessionState.LoggedOut }
        repository.login(payload())
        persistence.writeFailurePhase = SessionEnvelopePhase.PENDING

        val failure = runCatching {
            repository.login(payload().copy(accountId = 9, userId = 10))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(SessionState.LoggedOut, repository.sessionState.value)
        assertNull(repository.requestAuthSnapshot())
        assertTrue(persistence.cleared)
    }

    private fun payload() = SessionLoginPayload(
        companyId = 1,
        accountId = 2,
        userId = 3,
        userName = "Current user",
        headUrl = "avatar",
        userIdentity = 4,
        identityCardNumber = "330000199901011234",
        gender = 1,
        token = "secret-token",
    )

    private class FakePersistence(
        private var readResult: SessionEnvelopeReadResult,
        private val events: MutableList<String> = mutableListOf(),
    ) : SessionEnvelopePersistence {
        var cleared = false
        var writeFailurePhase: SessionEnvelopePhase? = null

        override suspend fun read(): SessionEnvelopeReadResult = readResult

        override suspend fun write(envelope: SessionEnvelope) {
            events += "write:${envelope.phase}"
            if (envelope.phase == writeFailurePhase) {
                throw IllegalStateException("uncertain ${envelope.phase} write")
            }
            readResult = SessionEnvelopeReadResult.Loaded(envelope)
        }

        override suspend fun clear() {
            cleared = true
            readResult = SessionEnvelopeReadResult.Missing
        }
    }

    private class FakeStorageRuntime(
        val events: MutableList<String> = mutableListOf(),
    ) : SessionStorageRuntime {
        var openFailure: Throwable? = null
        private var generation = 0L

        override suspend fun open(
            scopeKey: UserScopeKey,
            sessionEpoch: SessionEpoch,
        ): UserStorageLease {
            events += "open:${sessionEpoch.value}"
            openFailure?.let { throw it }
            return UserStorageLease(scopeKey, sessionEpoch, StorageGeneration(++generation))
        }

        override suspend fun revoke() {
            events += "revoke"
        }

        override suspend fun close() {
            events += "close"
        }
    }

    private class FakeEpochSource(start: Long) : SessionEpochSource {
        private var current = start

        override fun observe(epoch: SessionEpoch) {
            current = maxOf(current, epoch.value)
        }

        override fun next(): SessionEpoch = SessionEpoch(++current)
    }

    private class BlockingCutoverGate : UserStorageNamespaceCutoverGate {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()

        @Volatile
        override var isCompleted: Boolean = false
            private set

        override suspend fun ensureCompleted() {
            started.complete(Unit)
            release.await()
            isCompleted = true
        }
    }
}
