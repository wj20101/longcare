package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.features.location.core.LocationKeepAliveManager
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.result.ApiResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Business-owned session: one state monitor independent of screens and Android Service callbacks.
 * The Service executes uploads; neither this coordinator nor the AMap capability calls AddPosition.
 */
@Singleton
class LocationReportingManager @Inject constructor(
    private val keepAliveManager: LocationKeepAliveManager,
    private val orderRepository: OrderRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) : ServiceOrderLifecycle {
    private val lifecycleLock = Any()
    private var sequence = 0L
    private var current: LocationReportingSession? = null
    private var monitoringAllowed = false
    private val _orderState = MutableStateFlow<ServiceOrderStateModel?>(null)
    override val orderState = _orderState.asStateFlow()

    override fun monitorOrder(orderId: Long) {
        if (orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (monitoringAllowed) ensureSessionLocked(orderId)
        }
    }

    override fun onOrderStarted(orderId: Long) {
        if (orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (!monitoringAllowed) return
            val session = ensureSessionLocked(orderId)
            session.revision += 1
            session.setEnabled(true)
            _orderState.value = ServiceOrderStateModel(orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
            if (session.trackingRequested && !session.foregroundRequested) requestForegroundLocked(session)
        }
    }

    fun startReporting(orderKey: OrderKey) {
        if (orderKey.orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (!monitoringAllowed) return
            val session = ensureSessionLocked(orderKey.orderId)
            session.trackingRequested = true
            if (session.canUpload()) {
                // Explicit foreground retry only; the monitor never restarts a destroyed Service.
                requestForegroundLocked(session)
            }
        }
    }

    fun stopReporting() = synchronized(lifecycleLock) {
        stopLocked()
        _orderState.value = null
    }

    /** Shares the account lifecycle gate used by LocationTrackingManager, including UI-only monitors. */
    fun setMonitoringAllowed(allowed: Boolean) = synchronized(lifecycleLock) {
        monitoringAllowed = allowed
        if (!allowed) {
            stopLocked()
            _orderState.value = null
        }
    }

    override fun onOrderEnded(orderId: Long) = synchronized(lifecycleLock) {
        if (current?.orderId != orderId) return@synchronized
        stopLocked()
        _orderState.value = ServiceOrderStateModel(
            orderId = orderId,
            state = ServiceOrderStateModel.STATE_COMPLETED,
        )
    }

    internal fun sessionFor(owner: String, orderId: Long): LocationReportingSession? =
        synchronized(lifecycleLock) {
            current?.takeIf { it.owner == owner && it.orderId == orderId && it.canUpload() }
        }

    private fun ensureSessionLocked(orderId: Long): LocationReportingSession {
        current?.takeIf { it.orderId == orderId }?.let { return it }
        stopLocked()
        _orderState.value = null
        val session = LocationReportingSession(orderId, "location_report_${orderId}_${++sequence}")
        current = session
        session.monitorJob = scope.launch(start = CoroutineStart.LAZY) { monitor(session) }
        session.monitorJob?.start()
        return session
    }

    private suspend fun monitor(session: LocationReportingSession) {
        var retryDelayMs = POLL_INTERVAL_MS
        while (currentCoroutineContext().isActive) {
            val revision = synchronized(lifecycleLock) { session.revision }
            val result = try {
                orderRepository.getOrderState(session.orderId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ApiResult.Exception(error)
            }
            synchronized(lifecycleLock) {
                if (current !== session) return
                if (revision == session.revision && result is ApiResult.Success) {
                    val state = result.data.copy(orderId = session.orderId)
                    if (!state.isInProgress()) {
                        stopLocked()
                        _orderState.value = state
                        return
                    }
                    _orderState.value = state
                    session.setEnabled(true)
                    if (session.trackingRequested && !session.foregroundRequested) {
                        requestForegroundLocked(session)
                    }
                }
            }
            val waitMs = if (result is ApiResult.Success) {
                retryDelayMs = POLL_INTERVAL_MS
                POLL_INTERVAL_MS
            } else {
                logI("服务单状态暂不可用，保留会话并退避复核")
                retryDelayMs.also { retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS) }
            }
            // Unknown initial state stays paused; connectivity recovery is never terminal.
            delay(waitMs)
        }
    }

    private fun requestForegroundLocked(session: LocationReportingSession) {
        session.foregroundRequested = true
        keepAliveManager.acquireOrderTracking(session.owner, session.orderId)
    }

    private fun stopLocked() {
        val previous = current ?: return
        current = null
        // Cancel/gate synchronously BEFORE asking Android to asynchronously destroy its Service.
        previous.invalidate()
        if (previous.foregroundRequested) keepAliveManager.release(previous.owner)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        const val MAX_RETRY_MS = 60_000L
    }
}
