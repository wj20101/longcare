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
    private val _invalidations = MutableStateFlow<SessionInvalidation?>(null)
    private val lock = Any()
    private var invalidatedSessionKey: String? = null
    private var observedLoggedOutAfterInvalidation = false

    override val invalidations: StateFlow<SessionInvalidation?> = _invalidations

    init {
        applicationScope.launch {
            userSessionRepository.sessionState.collect { state ->
                synchronized(lock) {
                    when (state) {
                        SessionState.LoggedOut -> {
                            if (invalidatedSessionKey != null) {
                                observedLoggedOutAfterInvalidation = true
                            }
                        }

                        is SessionState.LoggedIn -> {
                            if (observedLoggedOutAfterInvalidation) {
                                invalidatedSessionKey = null
                                observedLoggedOutAfterInvalidation = false
                                _invalidations.value = null
                            }
                        }

                        SessionState.Unknown -> Unit
                    }
                }
            }
        }
    }

    override fun invalidate(reason: String) {
        val invalidation = synchronized(lock) {
            val user = userSessionRepository.sessionState.value.user ?: return
            val sessionKey = "${user.userId}:${user.token}"
            if (invalidatedSessionKey == sessionKey) {
                return
            }

            invalidatedSessionKey = sessionKey
            SessionInvalidation(
                id = nextId.incrementAndGet(),
                reason = reason,
            )
        }

        applicationScope.launch {
            try {
                userSessionRepository.logout()
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                logE("Failed to persist invalidated session logout", throwable = exception)
            }
            _invalidations.value = invalidation
        }
    }

    override fun consume(id: Long) {
        synchronized(lock) {
            val current = _invalidations.value
            if (current?.id == id) {
                _invalidations.compareAndSet(current, null)
            }
        }
    }
}
