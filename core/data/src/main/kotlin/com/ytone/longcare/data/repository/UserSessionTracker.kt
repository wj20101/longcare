package com.ytone.longcare.data.repository

import com.ytone.longcare.domain.faceauth.FaceVerificationSession
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserSessionSnapshot
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
    private var mutationInProgress = false
    private var pendingIdentity: List<Any?>? = null
    private val mutableGeneration = MutableStateFlow<Long?>(null)
    override val sessionGeneration = mutableGeneration.asStateFlow()
    private val mutableSnapshot = MutableStateFlow(UserSessionSnapshot(0L, null))
    internal val sessionSnapshot = mutableSnapshot.asStateFlow()
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
        mutationInProgress = true
        pendingIdentity = nextUser.identity()
        invalidate()
    }

    internal fun finishChange(user: User?) = synchronized(lock) {
        mutationInProgress = false
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

    internal fun invalidateOrderInfo(orderId: Long, expectedGeneration: Long?) = synchronized(lock) {
        // Check and invalidate under the same lock as login/logout. A late mutation from
        // the previous operator must not evict or interrupt the new operator's reads.
        if (expectedGeneration != null && isCurrent(expectedGeneration)) orderInfo.invalidate(orderId)
    }

    private fun publish(user: User?) {
        val next = user.identity()
        if (next == identity) {
            mutableSnapshot.value = UserSessionSnapshot(generation, user.takeUnless { mutationInProgress })
            return
        }
        invalidate()
        identity = next
        if (next != null) mutableGeneration.value = generation
        mutableSnapshot.value = UserSessionSnapshot(generation, user.takeUnless { mutationInProgress })
    }

    private fun invalidate() {
        identity = null
        generation++
        mutableGeneration.value = null
        mutableSnapshot.value = UserSessionSnapshot(generation, null)
        credentials.clear()
        orderInfo.clear()
    }

    private fun User?.identity(): List<Any?>? = this?.let {
        listOf(it.companyId, it.accountId, it.userId, it.token)
    }
}
