package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.faceauth.FaceVerificationSession
import com.ytone.longcare.model.User
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** No network or session-repository dependency: safe to invalidate before a session write. */
@Singleton
class UserSessionTracker @Inject constructor() : FaceVerificationSession {
    private val lock = Any()
    private var identity: List<Any?>? = null
    private var generation = 0L
    private var changing = false
    private var pendingIdentity: List<Any?>? = null
    private val mutableGeneration = MutableStateFlow<Long?>(null)
    override val sessionGeneration = mutableGeneration.asStateFlow()
    internal val credentials = TencentCredentialCache()
    internal val orderInfo = OrderInfoMemoryCache()

    internal fun observe(user: User?) = synchronized(lock) {
        // Publish the matching session before login state can create its screens.
        if (!changing || user.identity() == pendingIdentity) {
            changing = false
            pendingIdentity = null
            publish(user)
        }
    }

    internal fun beginChange(nextUser: User? = null) = synchronized(lock) {
        changing = true
        pendingIdentity = nextUser.identity()
        invalidate()
    }

    internal fun finishChange(user: User?) = synchronized(lock) {
        changing = false
        pendingIdentity = null
        publish(user)
    }

    internal fun matches(user: User?): Boolean = synchronized(lock) {
        !changing && identity == user.identity()
    }

    internal fun requireCurrent(expected: Long) {
        if (!isCurrent(expected)) throw CancellationException("User session changed")
    }

    private fun publish(user: User?) {
        val next = user.identity()
        if (next == identity) return
        invalidate()
        identity = next
        if (next != null) mutableGeneration.value = ++generation
    }

    private fun invalidate() {
        identity = null
        mutableGeneration.value = null
        credentials.clear()
        orderInfo.clear()
    }

    private fun User?.identity(): List<Any?>? = this?.let {
        listOf(it.companyId, it.accountId, it.userId, it.token)
    }
}
