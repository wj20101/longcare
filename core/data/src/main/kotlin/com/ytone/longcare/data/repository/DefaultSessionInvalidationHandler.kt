package com.ytone.longcare.data.repository

import com.ytone.longcare.common.network.SessionInvalidation
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultSessionInvalidationHandler @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : SessionInvalidationHandler {
    private val nextId = AtomicLong()
    private val mutableInvalidations = MutableStateFlow<SessionInvalidation?>(null)
    private val lock = Any()
    private val pendingGenerations = mutableSetOf<Long>()
    override val invalidations: StateFlow<SessionInvalidation?> = mutableInvalidations

    init {
        applicationScope.launch {
            userSessionRepository.sessionSnapshot.collect {
                synchronized(lock) {
                    val event = mutableInvalidations.value
                    if (event != null && !isCurrent(event)) mutableInvalidations.value = null
                }
            }
        }
    }

    override fun invalidate(reason: String, generation: Long) {
        synchronized(lock) {
            val current = userSessionRepository.sessionSnapshot.value
            if (current.user == null || current.generation != generation || !pendingGenerations.add(generation)) return
        }
        applicationScope.launch {
            try {
                // The comparison and durable clear share the repository's login/logout mutation mutex.
                val loggedOutGeneration = userSessionRepository.logoutIfCurrent(generation) ?: return@launch
                val event = SessionInvalidation(nextId.incrementAndGet(), reason, loggedOutGeneration)
                synchronized(lock) {
                    if (isCurrent(event)) mutableInvalidations.value = event
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logE("Failed to persist invalidated session logout", throwable = exception)
            } finally {
                synchronized(lock) { pendingGenerations.remove(generation) }
            }
        }
    }

    private fun isCurrent(event: SessionInvalidation): Boolean {
        val current = userSessionRepository.sessionSnapshot.value
        return current.user == null && current.generation == event.loggedOutGeneration &&
            userSessionRepository.sessionState.value == SessionState.LoggedOut
    }

    override fun consume(id: Long): Boolean = synchronized(lock) {
        val current = mutableInvalidations.value ?: return@synchronized false
        if (current.id != id) return@synchronized false
        mutableInvalidations.value = null
        isCurrent(current)
    }
}
