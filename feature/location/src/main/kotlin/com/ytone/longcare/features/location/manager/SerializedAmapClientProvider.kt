package com.ytone.longcare.features.location.manager

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.domain.location.LocationAccount
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import javax.inject.Inject
import javax.inject.Singleton

/** Tasks are always enqueued, including tasks with a zero delay. */
interface AmapCycleScheduler {
    fun now(): Long
    fun isMainThread(): Boolean
    fun post(delayMs: Long, task: Runnable)
    fun remove(task: Runnable)
}

class MainAmapCycleScheduler : AmapCycleScheduler {
    private val handler = Handler(Looper.getMainLooper())
    override fun now(): Long = SystemClock.elapsedRealtime()
    override fun isMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()
    override fun post(delayMs: Long, task: Runnable) { handler.postDelayed(task, delayMs) }
    override fun remove(task: Runnable) { handler.removeCallbacks(task) }
}

/**
 * Caller-owned handles share one native, bounded, single-location request. Continuous handles
 * schedule subsequent requests; their listeners, business clocks and sample history stay intact.
 * SDK stop has no acknowledgement API. The quiet period delays the next start after stop returns.
 */
@Singleton
class SerializedAmapClientProvider(
    private val rawFactory: AmapClientProvider,
    private val scheduler: AmapCycleScheduler,
    private val checkStartAllowed: () -> Unit = {},
    private val failureLocation: (Int) -> AMapLocation = ::newSchedulerFailureLocation,
    private val currentAccount: () -> LocationAccount? = { LocationAccount(0, 0, 0) },
) : AmapClientProvider {
    @Inject constructor(rawFactory: AmapLocationClientFactory, sessions: UserSessionRepository) : this(
        rawFactory, MainAmapCycleScheduler(), rawFactory::checkStartAllowed,
        currentAccount = {
            // Match the facade and order lifecycle's published business identity. Token refresh
            // changes sessionSnapshot.generation (and briefly clears its user), not this scope.
            sessions.sessionState.value.user?.let { LocationAccount(it.companyId, it.accountId, it.userId) }
        },
    )

    private inner class LogicalHandle(val apiKey: String, val account: LocationAccount) : AmapClientHandle {
        var option: AMapLocationClientOption? = null
        var listener: AMapLocationListener? = null
        var listenerGeneration = 0L
        var generation = 0L
        var running = false
        var destroyed = false
        var single = false
        var deadline = Long.MAX_VALUE
        var lastStart: Long? = null
        var nextDue = 0L
        var expiryTask: Runnable? = null
        var visibility: Boolean? = null

        override fun sdkVersion(): String? = if (destroyed) null else nativeClient?.sdkVersion()

        override fun notifyAppEnterForeBack(foreground: Boolean) {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            visibility = foreground
            // Preserve real transitions and the manager's bounded startup compensation.
            nativeClient?.notifyAppEnterForeBack(foreground)
        }

        override fun setLocationOption(option: AMapLocationClientOption) {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            check(!destroyed) { "amap_handle_destroyed" }
            this.option = option.clone()
            if (running && !single) {
                nextDue = lastStart?.let { safeAdd(it, interval(this)) } ?: scheduler.now()
                scheduleNext()
            }
        }

        override fun setLocationListener(listener: AMapLocationListener) {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            check(!destroyed) { "amap_handle_destroyed" }
            this.listener = listener
            listenerGeneration++
        }

        override fun unRegisterLocationListener(listener: AMapLocationListener) {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            if (this.listener !== listener) return
            this.listener = null
            listenerGeneration++
            detachFromFlight(this)
        }

        override fun startLocation() = startRequest(null)

        override fun startLocation(deadlineElapsedRealtime: Long) = startRequest(deadlineElapsedRealtime)

        private fun startRequest(callerDeadline: Long?) {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            check(!destroyed) { "amap_handle_destroyed" }
            check(!failed) { "amap_scheduler_unavailable" }
            if (running) return
            val configured = checkNotNull(option) { "amap_location_option_required" }
            checkNotNull(listener) { "amap_location_listener_required" }
            running = true
            generation++
            single = configured.isOnceLocation
            val now = scheduler.now()
            deadline = if (single) minOf(safeAdd(now, requestBudget(configured)), callerDeadline ?: Long.MAX_VALUE)
                else Long.MAX_VALUE
            if (single && now >= deadline) { running = false; return }
            nextDue = now
            if (single) scheduleExpiry(this)
            val current = flight
            if (current != null && now < current.deadline) join(current, this)
            else {
                if (current != null) finish(current)?.let { failOwners(it) }
                scheduleNext()
            }
        }

        override fun stopLocation() {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            running = false
            generation++
            cancelExpiry(this)
            detachFromFlight(this)
            scheduleNext()
        }

        override fun onDestroy() {
            checkMain()
            if (destroyed) return
            if (refreshScope() != account || destroyed) return
            try { stopLocation() } finally {
                destroyed = true
                listener = null
                listenerGeneration++
                owners.remove(this)
                if (owners.isEmpty()) destroyNative()
            }
        }
    }

    private inner class Recipient(val owner: LogicalHandle) {
        val generation = owner.generation
        val listenerGeneration = owner.listenerGeneration
        val listener = checkNotNull(owner.listener)
        fun stillRegistered(): Boolean = !owner.destroyed && owner.generation == generation &&
            owner.listenerGeneration == listenerGeneration && owner.listener === listener
        fun participating(): Boolean = stillRegistered() && owner.running
    }

    private inner class Flight(val startedAt: Long, val deadline: Long, val account: LocationAccount) {
        val recipients = linkedMapOf<LogicalHandle, Recipient>()
        lateinit var listener: AMapLocationListener
        var timeoutTask: Runnable? = null
    }

    private val owners = linkedSetOf<LogicalHandle>()
    private var nativeClient: AmapClientHandle? = null
    private var flight: Flight? = null
    private var nextTask: Runnable? = null
    private var quietUntil = 0L
    private var closing = false
    private var failed = false
    // Unlike a rejected start, a failed cleanup leaves native ownership uncertain for this
    // process. Clearing handles or changing accounts must never make that state reusable.
    private var cleanupFailureLatched = false
    private var scopeKnown = false
    private var observedAccount: LocationAccount? = null

    override fun create(apiKey: String): AmapClientHandle {
        checkMain()
        val account = checkNotNull(refreshScope()) { "amap_account_unavailable" }
        check(!failed) { "amap_scheduler_unavailable" }
        check(owners.all { it.apiKey == apiKey }) { "amap_client_key_changed_while_owned" }
        return LogicalHandle(apiKey, account).also(owners::add)
    }

    private fun refreshScope(): LocationAccount? {
        val account = currentAccount()
        if (!scopeKnown) {
            scopeKnown = true
            observedAccount = account
        } else if (observedAccount != account) {
            observedAccount = account
            nextTask?.let(scheduler::remove)
            nextTask = null
            // Invalidate recipients before SDK cleanup. Old callers' later finally blocks are
            // then no-ops and cannot close a replacement account's native client or queued start.
            owners.forEach { owner ->
                owner.running = false
                owner.destroyed = true
                owner.generation++
                owner.listenerGeneration++
                owner.listener = null
                cancelExpiry(owner)
            }
            owners.clear()
            val cleanupError = flight?.let(::finish)
            val destroyError = runCatching { destroyNative() }.exceptionOrNull()
            // A scope change must not heal a failed stop by merely clearing owner bookkeeping.
            if (cleanupError != null || destroyError != null) failed = true
        }
        return account
    }

    private fun scheduleExpiry(owner: LogicalHandle) {
        cancelExpiry(owner)
        val generation = owner.generation
        val task = Runnable {
            if (!owner.destroyed && owner.running && owner.generation == generation &&
                scheduler.now() >= owner.deadline) {
                owner.expiryTask = null
                owner.running = false
                detachFromFlight(owner)
                // The existing acquisition deadline returns TIMEOUT; this is not an SDK callback.
                scheduleNext()
            }
        }
        owner.expiryTask = task
        scheduler.post((owner.deadline - scheduler.now()).coerceAtLeast(0L), task)
    }

    private fun cancelExpiry(owner: LogicalHandle) {
        owner.expiryTask?.let(scheduler::remove)
        owner.expiryTask = null
    }

    private fun join(current: Flight, owner: LogicalHandle) {
        if (refreshScope() != current.account || owner.account != current.account) return
        if (!owner.running || owner.destroyed || owner.listener == null) return
        if (owner.single && scheduler.now() >= owner.deadline) return
        current.recipients[owner] = Recipient(owner)
        owner.lastStart = current.startedAt
        if (!owner.single) owner.nextDue = safeAdd(current.startedAt, interval(owner))
    }

    private fun detachFromFlight(owner: LogicalHandle) {
        val current = flight ?: return
        current.recipients.remove(owner)
        if (current.recipients.values.none { it.participating() }) {
            finish(current)?.let { failOwners(it) }
        }
    }

    private fun scheduleNext() {
        nextTask?.let(scheduler::remove)
        nextTask = null
        if (failed || closing || flight != null) return
        val now = scheduler.now()
        val eligible = owners.filter { it.running && !it.destroyed && it.listener != null &&
            (!it.single || now < it.deadline) }
        val due = eligible.minOfOrNull { it.nextDue } ?: return
        val startAt = maxOf(due, quietUntil)
        val task = Runnable {
            nextTask = null
            beginIfDue()
        }
        nextTask = task
        scheduler.post((startAt - now).coerceAtLeast(0L), task)
    }

    private fun beginIfDue() {
        checkMain()
        if (refreshScope() == null) return
        if (failed || closing || flight != null) return
        val now = scheduler.now()
        if (now < quietUntil) { scheduleNext(); return }
        val eligible = owners.filter { it.running && !it.destroyed && it.listener != null &&
            (!it.single || now < it.deadline) }
        val due = eligible.filter { it.nextDue <= now }
        if (due.isEmpty()) { scheduleNext(); return }
        // A reporting request uses its normal 30s SDK budget. Joining callers keep their own
        // earlier deadlines; joining never extends the request already running in the SDK.
        val origin = due.firstOrNull { !it.single } ?: due.first()
        val option = checkNotNull(origin.option).clone().apply {
            isOnceLocation = true
            isOnceLocationLatest = true
            isLocationCacheEnable = false
            httpTimeOut = if (origin.single) (origin.deadline - now).coerceIn(1L, MAX_REQUEST_MS)
                else requestBudget(this)
        }
        val deadline = if (origin.single) origin.deadline else safeAdd(now, requestBudget(option))
        val current = Flight(now, deadline, origin.account)
        current.listener = AMapLocationListener { raw ->
            // Marshal callbacks, including synchronous SDK callbacks, through this one Main queue.
            scheduler.post(0L, Runnable { onLocation(current, raw) })
        }
        eligible.forEach { join(current, it) }
        if (current.recipients.isEmpty() || refreshScope() != current.account) return
        flight = current
        val timeout = Runnable {
            if (flight === current) {
                finish(current)?.let { failOwners(it) }
                scheduleNext()
            }
        }
        current.timeoutTask = timeout
        scheduler.post((deadline - scheduler.now()).coerceAtLeast(0L), timeout)
        try {
            checkStartAllowed()
            if (!isCurrent(current)) return
            if (scheduler.now() >= deadline) { finish(current)?.let { failOwners(it) }; scheduleNext(); return }
            val created = nativeClient == null
            val client = nativeClient ?: rawFactory.create(origin.apiKey).also { nativeClient = it }
            if (!isCurrent(current)) return
            if (scheduler.now() >= deadline) { finish(current)?.let { failOwners(it) }; scheduleNext(); return }
            client.setLocationOption(option)
            if (!isCurrent(current)) return
            if (scheduler.now() >= deadline) { finish(current)?.let { failOwners(it) }; scheduleNext(); return }
            client.setLocationListener(current.listener)
            if (!isCurrent(current)) return
            if (scheduler.now() >= deadline) { finish(current)?.let { failOwners(it) }; scheduleNext(); return }
            client.startLocation()
            if (created) eligible.lastOrNull { it.visibility != null }?.visibility?.let { visible ->
                runCatching { client.notifyAppEnterForeBack(visible) }
                    .onFailure { recordFailure("initial_visibility", it) }
            }
        } catch (error: Exception) {
            val cleanupError = finish(current)
            failOwners(cleanupError ?: error)
        }
    }

    private fun onLocation(current: Flight, raw: AMapLocation?) {
        checkMain()
        if (!isCurrent(current)) return
        val inBudget = scheduler.now() < current.deadline
        val recipients = current.recipients.values.filter { it.participating() }
        val cleanupError = finish(current)
        if (cleanupError != null) { failOwners(cleanupError); return }
        recipients.forEach { recipient ->
            val owner = recipient.owner
            if (owner.single) {
                owner.running = false
                cancelExpiry(owner)
            }
        }
        if (inBudget && scheduler.now() < current.deadline) {
            recipients.forEach { recipient ->
                val owner = recipient.owner
                // Earlier recipients may synchronously stop/destroy other handles or replace a
                // listener. Recheck each recipient, not only the original delivery snapshot.
                if (refreshScope() == current.account && recipient.stillRegistered() && scheduler.now() < current.deadline &&
                    (!owner.single || scheduler.now() < owner.deadline)) {
                    runCatching { recipient.listener.onLocationChanged(raw) }
                        .onFailure { recordFailure("listener_delivery", it) }
                }
            }
        }
        scheduleNext()
    }

    /** Invalidates the flight before invoking SDK cleanup, and attempts both cleanup steps. */
    private fun finish(current: Flight): Throwable? {
        if (flight !== current) return null
        flight = null
        current.timeoutTask?.let(scheduler::remove)
        current.timeoutTask = null
        closing = true
        var failure: Throwable? = null
        try {
            nativeClient?.let { client ->
                runCatching { client.unRegisterLocationListener(current.listener) }.onFailure {
                    failure = it
                    recordFailure("unregister_listener", it)
                }
                runCatching { client.stopLocation() }.onFailure {
                    if (failure == null) failure = it
                    recordFailure("stop_location", it)
                }
                quietUntil = maxOf(quietUntil, safeAdd(scheduler.now(), QUIET_MS))
            }
        } finally { closing = false }
        if (failure != null) cleanupFailureLatched = true
        return failure
    }

    private fun failOwners(error: Throwable) {
        failed = true
        nextTask?.let(scheduler::remove)
        nextTask = null
        recordFailure("request_failed", error)
        val recipients = owners.filter { it.running && !it.destroyed && it.listener != null }.map(::Recipient)
        owners.forEach { it.running = false; cancelExpiry(it) }
        val code = if (error is SecurityException) AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION
            else AMapLocation.ERROR_CODE_FAILURE_INIT
        recipients.forEach { recipient ->
            if (refreshScope() == recipient.owner.account && recipient.stillRegistered()) {
                runCatching { recipient.listener.onLocationChanged(failureLocation(code)) }
                    .onFailure { recordFailure("failure_delivery", it) }
            }
        }
        // Keep the failed native instance isolated until all its logical owners explicitly close.
        // There is no automatic rebuild or later request on this instance.
    }

    private fun destroyNative() {
        nextTask?.let(scheduler::remove)
        nextTask = null
        val current = flight
        if (current != null) finish(current)?.let { failed = true }
        val client = nativeClient
        nativeClient = null
        if (client == null) {
            // The last owner can release a start rejected before native creation. Future
            // explicit requests may retry after permissions/configuration recover.
            failed = cleanupFailureLatched
            return
        }
        try {
            client.onDestroy()
            failed = cleanupFailureLatched
        } catch (error: Throwable) {
            cleanupFailureLatched = true
            failed = true
            recordFailure("destroy_client", error)
            throw error
        } finally {
            quietUntil = maxOf(quietUntil, safeAdd(scheduler.now(), QUIET_MS))
        }
    }

    private fun recordFailure(stage: String, error: Throwable) {
        runCatching {
            LocationEventTracker.trackError(LocationEventTracker.EventType.CLIENT_CLEANUP_ERROR,
                throwable = error, extras = mapOf("cleanupStage" to stage, "clientPurpose" to "shared_single_request"))
        }
    }

    private fun interval(owner: LogicalHandle): Long = checkNotNull(owner.option).interval.coerceAtLeast(QUIET_MS)
    private fun isCurrent(current: Flight): Boolean = refreshScope() == current.account && flight === current && !failed
    private fun requestBudget(option: AMapLocationClientOption): Long = option.httpTimeOut.coerceIn(1L, MAX_REQUEST_MS)
    private fun checkMain() = check(scheduler.isMainThread()) { "amap_scheduler_requires_main" }

    private companion object {
        const val QUIET_MS = 1_000L
        const val MAX_REQUEST_MS = 30_000L
        fun safeAdd(value: Long, delta: Long): Long = if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta
    }
}

private fun newSchedulerFailureLocation(code: Int) = AMapLocation("amap_scheduler").apply {
    errorCode = code
    errorInfo = "location_scheduler_unavailable"
}
