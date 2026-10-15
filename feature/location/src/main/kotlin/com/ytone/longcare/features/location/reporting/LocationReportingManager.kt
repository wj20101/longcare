package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.location.LocationRecoveryRecord
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import java.util.UUID
import kotlinx.coroutines.channels.Channel
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
import kotlinx.coroutines.ensureActive
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
    private val recoveryStore: LocationRecoveryStore,
) : ServiceOrderLifecycle {
    private val lifecycleLock = Any()
    private var current: LocationReportingSession? = null
    private var monitoringAllowed = false
    private var account: LocationAccount? = null
    private var lifecycleRevision = 0L
    // One serialized, conflated identity writer; no coordinate queue and no initial clearing on boot.
    private val recoveryWrites = Channel<LocationRecoveryRecord?>(Channel.CONFLATED)
    private var lastRecoveryRecord: LocationRecoveryRecord? = null
    init {
        scope.launch {
            for (record in recoveryWrites) {
                try { if (record == null) recoveryStore.clear() else recoveryStore.save(record) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    synchronized(lifecycleLock) { if (lastRecoveryRecord == record) lastRecoveryRecord = null }
                    logI("定位恢复信息保存失败: ${error.javaClass.simpleName}")
                }
            }
        }
    }

    fun setAccount(value: LocationAccount?) = synchronized(lifecycleLock) {
        if (account != null && account != value) stopLocked()
        val accountChanged = account != value
        account = value
        monitoringAllowed = value != null
        if (value == null) {
            stopLocked()
            recoveryWrites.trySend(null)
            _orderState.value = null
        } else {
            if (_orderState.value?.isInProgress() == true) current?.setEnabled(true)
            if (accountChanged && current == null) {
                val revision = lifecycleRevision
                scope.launch {
                    val record = try { recoveryStore.read() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                    synchronized(lifecycleLock) {
                        if (account == value && current == null && revision == lifecycleRevision) {
                            _pendingRecovery.value = record?.takeIf { it.account == value }
                        }
                    }
                }
            }
        }
    }

    fun pauseAuthorization() = synchronized(lifecycleLock) {
        monitoringAllowed = false
        current?.setEnabled(false)
    }

    internal fun restore(record: LocationRecoveryRecord, identity: LocationAccount): Pair<LocationReportingSession, Long>? =
        synchronized(lifecycleLock) {
            if (record.account != identity || (account != null && account != identity)) return@synchronized null
            if (current?.owner?.let { it != record.sessionId } == true) return@synchronized null
            account = identity
            monitoringAllowed = true
            val session = ensureSessionLocked(record.orderId, record.sessionId)
            session.trackingRequested = true
            // Adopt BEFORE starting the query: even an immediate non-IP response can stop the Service.
            val generation = keepAliveManager.adoptRestoredService(session.owner)
            session.monitorJob?.start()
            session to generation
        }

    override fun prepareOrderStart(orderId: Long) = synchronized(lifecycleLock) {
        if (!monitoringAllowed || orderId <= 0) return@synchronized
        val session = ensureSessionLocked(orderId)
        session.preparingStart = true
        session.trackingRequested = true
        requestForegroundLocked(session)
        session.monitorJob?.start()
    }

    override fun cancelOrderPreparation(orderId: Long) = synchronized(lifecycleLock) {
        val session = current?.takeIf { it.orderId == orderId } ?: return@synchronized
        session.preparingStart = false
        if (!session.canUpload()) stopLocked()
    }

    private val _pendingRecovery = MutableStateFlow<LocationRecoveryRecord?>(null)
    val pendingRecovery = _pendingRecovery.asStateFlow()
    private val _status = MutableStateFlow(LocationReportingStatus.IDLE)
    val status = _status.asStateFlow()

    internal fun updateStatus(owner: String, value: LocationReportingStatus) = synchronized(lifecycleLock) {
        if (current?.owner == owner) _status.value = value
    }
    internal fun onServiceInterrupted(owner: String) = synchronized(lifecycleLock) {
        if (current?.owner == owner && current?.trackingRequested == true) _status.value = LocationReportingStatus.INTERRUPTED
    }

    fun onAppVisible() = synchronized(lifecycleLock) {
        current?.takeIf { monitoringAllowed && it.trackingRequested }?.let(::requestForegroundLocked)
    }

    fun resumePendingRecovery() = synchronized(lifecycleLock) {
        val record = _pendingRecovery.value ?: return@synchronized
        if (!monitoringAllowed || record.account != account || current != null) return@synchronized
        val session = ensureSessionLocked(record.orderId, record.sessionId)
        session.trackingRequested = true
        _pendingRecovery.value = null
        requestForegroundLocked(session)
        session.monitorJob?.start()
    }

    private val _orderState = MutableStateFlow<ServiceOrderStateModel?>(null)
    override val orderState = _orderState.asStateFlow()

    override fun monitorOrder(orderId: Long) {
        if (orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (monitoringAllowed) ensureSessionLocked(orderId).monitorJob?.start()
        }
    }

    override fun onOrderStarted(orderId: Long) {
        if (orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (!monitoringAllowed) return
            val session = ensureSessionLocked(orderId)
            session.revision += 1
            session.setEnabled(true)
            session.preparingStart = false
            persistLocked(session)
            _orderState.value = ServiceOrderStateModel(orderId, ServiceOrderStateModel.STATE_IN_PROGRESS)
            session.monitorJob?.start()
        }
    }

    fun startReporting(orderKey: OrderKey) {
        if (orderKey.orderId <= 0L) return
        synchronized(lifecycleLock) {
            if (!monitoringAllowed) return
            val session = ensureSessionLocked(orderKey.orderId)
            session.trackingRequested = true
            // Prepare while the caller is visible, BEFORE any suspended order-state query returns.
            requestForegroundLocked(session)
            session.monitorJob?.start()
            if (session.canUpload()) persistLocked(session)
        }
    }

    fun stopReporting() = synchronized(lifecycleLock) {
        // Explicit stop also clears a cold-start record whose Service has not adopted it yet.
        if (current == null) recoveryWrites.trySend(null)
        stopLocked()
        _orderState.value = null
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
            current?.takeIf { it.owner == owner && it.orderId == orderId && it.isValid() }
        }

    private fun ensureSessionLocked(orderId: Long, restoredOwner: String? = null): LocationReportingSession {
        current?.takeIf { it.orderId == orderId }?.let { return it }
        stopLocked()
        _orderState.value = null
        val session = LocationReportingSession(
            orderId,
            restoredOwner ?: "location_report_${orderId}_${UUID.randomUUID()}",
            checkNotNull(account) { "location_account_not_ready" },
        )
        current = session
        _pendingRecovery.value = null
        _status.value = LocationReportingStatus.PREPARING
        session.monitorJob = scope.launch(start = CoroutineStart.LAZY) { monitor(session) }
        return session
    }

    private suspend fun monitor(session: LocationReportingSession) {
        var retryDelayMs = POLL_INTERVAL_MS
        while (currentCoroutineContext().isActive) {
            if (!synchronized(lifecycleLock) { monitoringAllowed }) {
                delay(POLL_INTERVAL_MS)
                continue
            }
            val revision = synchronized(lifecycleLock) { session.revision }
            val result = try {
                orderRepository.getOrderState(session.orderId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ApiResult.Exception(error)
            }
            currentCoroutineContext().ensureActive()
            synchronized(lifecycleLock) {
                if (current !== session) return
                if (revision == session.revision && result is ApiResult.Success) {
                    val state = result.data.copy(orderId = session.orderId)
                    if (!state.isInProgress()) {
                        if (session.preparingStart && state.state == ServiceOrderStateModel.STATE_PENDING) {
                            return@synchronized
                        }
                        stopLocked()
                        _orderState.value = state
                        return
                    }
                    _orderState.value = state
                    session.setEnabled(monitoringAllowed)
                    if (session.trackingRequested && monitoringAllowed) persistLocked(session)
                    // Never initiate a NEW foreground service from an asynchronous/background poll.
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
        if (!keepAliveManager.acquireOrderTracking(session.owner, session.orderId)) _status.value = LocationReportingStatus.INTERRUPTED
        else if (_status.value == LocationReportingStatus.INTERRUPTED) _status.value = LocationReportingStatus.PREPARING
    }

    private fun stopLocked() {
        lifecycleRevision += 1
        _pendingRecovery.value = null
        _status.value = LocationReportingStatus.IDLE
        val previous = current ?: return
        current = null
        // Cancel/gate synchronously BEFORE asking Android to asynchronously destroy its Service.
        previous.invalidate()
        lastRecoveryRecord = null
        recoveryWrites.trySend(null)
        keepAliveManager.release(previous.owner)
    }

    private fun persistLocked(session: LocationReportingSession) {
        if (!session.canUpload() || !session.trackingRequested) return
        val record = LocationRecoveryRecord(session.account, session.orderId, session.owner)
        if (lastRecoveryRecord != record) {
            lastRecoveryRecord = record
            recoveryWrites.trySend(record)
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 30_000L
        const val MAX_RETRY_MS = 60_000L
    }
}

enum class LocationReportingStatus { IDLE, PREPARING, LOCATING, ACTIVE, LOW_PRECISION, NETWORK_ERROR, INTERRUPTED }
