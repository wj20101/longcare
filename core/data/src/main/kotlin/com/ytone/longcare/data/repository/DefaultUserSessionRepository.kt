package com.ytone.longcare.data.repository

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.data.session.EncryptedSessionEnvelopeStore
import com.ytone.longcare.data.session.FaceSetupIdentitySecret
import com.ytone.longcare.data.session.RequestAuthSnapshot
import com.ytone.longcare.data.session.SessionEnvelope
import com.ytone.longcare.data.session.SessionEnvelopePersistence
import com.ytone.longcare.data.session.SessionEnvelopePhase
import com.ytone.longcare.data.session.SessionEnvelopeReadResult
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.data.startup.AssumedCompletedCutoverGate
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.SessionRuntimeReadyHook
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.SessionLoginPayload
import com.ytone.longcare.model.UserScopeKey
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal interface SessionStorageRuntime {
    suspend fun open(scopeKey: UserScopeKey, sessionEpoch: SessionEpoch): UserStorageLease
    suspend fun revoke()
    suspend fun close()
}

private class RegistrySessionStorageRuntime(
    private val registry: UserStorageRegistry,
) : SessionStorageRuntime {
    override suspend fun open(scopeKey: UserScopeKey, sessionEpoch: SessionEpoch): UserStorageLease =
        registry.open(scopeKey, sessionEpoch)

    override suspend fun revoke() = registry.revoke()

    override suspend fun close() = registry.close()
}

internal interface SessionEpochSource {
    fun observe(epoch: SessionEpoch)
    fun next(): SessionEpoch
}

private class MonotonicSessionEpochSource : SessionEpochSource {
    private val last = AtomicLong(System.currentTimeMillis().coerceAtLeast(1L))

    override fun observe(epoch: SessionEpoch) {
        last.updateAndGet { current -> maxOf(current, epoch.value) }
    }

    override fun next(): SessionEpoch = SessionEpoch(
        last.updateAndGet { current -> maxOf(current + 1, System.currentTimeMillis()) }
    )
}

/**
 * Coordinates encrypted session durability with the physical user-storage lifecycle.
 * Public state is published only after the requested namespace is Ready.
 */
@Singleton
class DefaultUserSessionRepository internal constructor(
    private val persistence: SessionEnvelopePersistence,
    private val storageRuntime: SessionStorageRuntime,
    private val applicationScope: CoroutineScope,
    private val epochSource: SessionEpochSource,
    private val cleanupHooks: () -> Set<SessionRuntimeCleanupHook> = { emptySet() },
    private val readyHooks: () -> Set<SessionRuntimeReadyHook> = { emptySet() },
    private val cutoverGate: UserStorageNamespaceCutoverGate = AssumedCompletedCutoverGate,
) : UserSessionRepository, SessionSecretProvider {
    @Inject
    constructor(
        persistence: EncryptedSessionEnvelopeStore,
        storageRegistry: UserStorageRegistry,
        @ApplicationScope applicationScope: CoroutineScope,
        cleanupHooks: Provider<Set<@JvmSuppressWildcards SessionRuntimeCleanupHook>>,
        readyHooks: Provider<Set<@JvmSuppressWildcards SessionRuntimeReadyHook>>,
        cutoverGate: UserStorageNamespaceCutoverGate,
    ) : this(
        persistence = persistence,
        storageRuntime = RegistrySessionStorageRuntime(storageRegistry),
        applicationScope = applicationScope,
        epochSource = MonotonicSessionEpochSource(),
        cleanupHooks = cleanupHooks::get,
        readyHooks = readyHooks::get,
        cutoverGate = cutoverGate,
    )

    private val lifecycleMutex = Mutex()
    private val mutableSessionState = MutableStateFlow<SessionState>(SessionState.Unknown)
    private val activeEnvelope = AtomicReference<SessionEnvelope?>(null)
    private var readyJob: Job? = null

    override val sessionState: StateFlow<SessionState> = mutableSessionState.asStateFlow()

    init {
        applicationScope.launch { restoreFromDisk() }
    }

    override suspend fun login(payload: SessionLoginPayload) {
        cutoverGate.ensureCompleted()
        lifecycleMutex.withLock {
        val epoch = epochSource.next()
        val pending = SessionEnvelope(
            phase = SessionEnvelopePhase.PENDING,
            sessionEpoch = epoch,
            payload = payload,
        )
        val previous = activeEnvelope.get()

        try {
            // PENDING and the complete payload are one encrypted, atomic commit. If the
            // outcome is uncertain, the catch path deliberately fails the process closed.
            persistence.write(pending)
            mutableSessionState.value = SessionState.Unknown
            activeEnvelope.set(null)
            stopRuntimeReady()
            storageRuntime.revoke()
            cleanupRuntime(previous)
            storageRuntime.close()
            val lease = storageRuntime.open(payload.scopeKey, epoch)
            val active = pending.copy(phase = SessionEnvelopePhase.ACTIVE)
            persistence.write(active)
            activeEnvelope.set(active)
            mutableSessionState.value = SessionState.LoggedIn(payload.toCurrentUser())
            startRuntimeReady(active, lease)
        } catch (error: Throwable) {
            mutableSessionState.value = SessionState.Unknown
            val cleanupFailure = cleanupToLoggedOut(listOfNotNull(previous, pending))
            throw mergeFailures(error, cleanupFailure)
        }
        }
    }

    override suspend fun logout() {
        cutoverGate.ensureCompleted()
        lifecycleMutex.withLock {
        mutableSessionState.value = SessionState.Unknown
        val previous = activeEnvelope.getAndSet(null)
        cleanupToLoggedOut(listOfNotNull(previous))?.let { throw it }
        }
    }

    override fun requestAuthSnapshot(): RequestAuthSnapshot? = activeEnvelope.get()
        ?.takeIf { cutoverGate.isCompleted }
        ?.takeIf { it.phase == SessionEnvelopePhase.ACTIVE }
        ?.let { envelope ->
            RequestAuthSnapshot(
                scopeKey = envelope.payload.scopeKey,
                userIdentity = envelope.payload.userIdentity,
                token = envelope.payload.token,
                sessionEpoch = envelope.sessionEpoch,
            )
        }

    override fun faceSetupIdentity(): FaceSetupIdentitySecret? = activeEnvelope.get()
        ?.takeIf { cutoverGate.isCompleted }
        ?.takeIf { it.phase == SessionEnvelopePhase.ACTIVE }
        ?.payload
        ?.let { payload ->
            FaceSetupIdentitySecret(
                userId = payload.userId,
                userName = payload.userName,
                identityCardNumber = payload.identityCardNumber,
            )
        }

    override fun activeSessionFingerprint(): String? = activeEnvelope.get()
        ?.takeIf { cutoverGate.isCompleted }
        ?.takeIf { it.phase == SessionEnvelopePhase.ACTIVE }
        ?.let { "${it.payload.scopeKey.namespaceId().value}:${it.sessionEpoch.value}" }

    private suspend fun restoreFromDisk() = lifecycleMutex.withLock {
        cutoverGate.ensureCompleted()
        when (val result = persistence.read()) {
            SessionEnvelopeReadResult.Missing -> publishLoggedOut()
            is SessionEnvelopeReadResult.Rejected -> {
                try {
                    persistence.clear()
                } catch (cancellation: CancellationException) {
                    publishLoggedOut()
                    throw cancellation
                } catch (_: Exception) {
                    KLogger.w(
                        tag = "UserSession",
                        message = "Unreadable session cleanup failed; session remains logged out.",
                    )
                }
                publishLoggedOut()
            }
            is SessionEnvelopeReadResult.Loaded -> restore(result.envelope)
        }
    }

    private suspend fun restore(envelope: SessionEnvelope) {
        epochSource.observe(envelope.sessionEpoch)
        if (envelope.phase == SessionEnvelopePhase.PENDING) {
            cleanupToLoggedOut(listOf(envelope))?.let { throw it }
            return
        }

        try {
            val lease = storageRuntime.open(envelope.payload.scopeKey, envelope.sessionEpoch)
            activeEnvelope.set(envelope)
            mutableSessionState.value = SessionState.LoggedIn(envelope.payload.toCurrentUser())
            startRuntimeReady(envelope, lease)
        } catch (error: Throwable) {
            val combined = mergeFailures(
                primary = error,
                secondary = cleanupToLoggedOut(listOf(envelope)),
            )
            if (combined is CancellationException || combined !is Exception) throw combined
        }
    }

    private suspend fun cleanupToLoggedOut(failedEnvelopes: Iterable<SessionEnvelope>): Throwable? =
        withContext(NonCancellable) {
            activeEnvelope.set(null)
            var failure: Throwable? = null
            failure = collectFailure(failure) { stopRuntimeReady() }
            failure = collectFailure(failure) { storageRuntime.revoke() }
            failedEnvelopes
                .distinctBy { envelope ->
                    SessionRuntimeIdentity(
                        scopeKey = envelope.payload.scopeKey,
                        sessionEpoch = envelope.sessionEpoch,
                    )
                }
                .forEach { envelope ->
                    failure = collectFailure(failure) { cleanupRuntime(envelope) }
                }
            failure = collectFailure(failure) { storageRuntime.close() }
            failure = collectFailure(failure) { persistence.clear() }
            mutableSessionState.value = SessionState.LoggedOut
            failure
        }

    private suspend fun cleanupRuntime(envelope: SessionEnvelope?) {
        if (envelope == null) return
        val identity = SessionRuntimeIdentity(
            scopeKey = envelope.payload.scopeKey,
            sessionEpoch = envelope.sessionEpoch,
        )
        var failure: Throwable? = null
        cleanupHooks().forEach { hook ->
            failure = collectFailure(failure) { hook.cleanup(identity) }
        }
        failure?.let { throw it }
    }

    private fun startRuntimeReady(
        envelope: SessionEnvelope,
        lease: UserStorageLease,
    ) {
        val identity = SessionRuntimeIdentity(
            scopeKey = envelope.payload.scopeKey,
            sessionEpoch = envelope.sessionEpoch,
        )
        readyJob = applicationScope.launch {
            readyHooks().forEach { hook -> hook.onReady(identity, lease) }
        }
    }

    private suspend fun stopRuntimeReady() {
        val job = readyJob
        readyJob = null
        withContext(NonCancellable) { job?.cancelAndJoin() }
    }

    private suspend fun collectFailure(
        current: Throwable?,
        action: suspend () -> Unit,
    ): Throwable? = try {
        action()
        current
    } catch (error: Throwable) {
        when {
            current == null -> error
            current is CancellationException -> current.also { it.addSuppressed(error) }
            error is CancellationException -> error.also { it.addSuppressed(current) }
            else -> current.also { it.addSuppressed(error) }
        }
    }

    private fun mergeFailures(primary: Throwable, secondary: Throwable?): Throwable = when {
        secondary == null -> primary
        primary is CancellationException -> primary.also { it.addSuppressed(secondary) }
        secondary is CancellationException -> secondary.also { it.addSuppressed(primary) }
        else -> primary.also { it.addSuppressed(secondary) }
    }

    private fun publishLoggedOut() {
        activeEnvelope.set(null)
        mutableSessionState.value = SessionState.LoggedOut
    }
}
