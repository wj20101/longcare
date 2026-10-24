package com.ytone.longcare.features.location.manager

import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.ytone.longcare.domain.location.LocationAccount
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.PriorityQueue
import org.junit.Assert.*
import org.junit.Test

class SerializedAmapClientProviderTest {
    @Test fun `continuous cycles stop before delivery and wait for the quiet gap`() {
        val fixture = Fixture()
        val original = option().apply {
            isOnceLocationLatest = false
            isLocationCacheEnable = true
            isNeedAddress = true
            httpTimeOut = 30_000L
        }
        var delivered = 0
        val stopsAtDelivery = mutableListOf<Int>()
        val owner = fixture.owner(original) {
            delivered++
            stopsAtDelivery += fixture.raw.stops
        }
        fixture.start(owner)
        val raw = fixture.raw
        assertEquals(1, raw.starts)
        fixture.scheduler.advanceBy(29_900L)
        raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, delivered)
        assertEquals(1, raw.stops)
        assertEquals(listOf(1), stopsAtDelivery)
        fixture.scheduler.advanceBy(999L)
        assertEquals(1, raw.starts)
        fixture.scheduler.advanceBy(1L)
        assertEquals(2, raw.starts)
        assertEquals(1, fixture.factory.clients.size)
        assertFalse(original.isOnceLocation)
        assertFalse(original.isOnceLocationLatest)
        assertTrue(original.isLocationCacheEnable)
        raw.options.forEach {
            assertTrue(it.isOnceLocation)
            assertTrue(it.isOnceLocationLatest)
            assertFalse(it.isLocationCacheEnable)
            assertEquals(original.locationMode, it.locationMode)
            assertEquals(original.isNeedAddress, it.isNeedAddress)
        }
        owner.onDestroy()
    }

    @Test fun `single and continuous owners share a flight and either owner can cancel independently`() {
        listOf(false, true).forEach { cancelSingle ->
            val fixture = Fixture()
            val continuousResults = mutableListOf<AMapLocation?>()
            val singleResults = mutableListOf<AMapLocation?>()
            val continuous = fixture.owner(option()) { continuousResults += it }
            val single = fixture.owner(option(single = true)) { singleResults += it }
            fixture.start(continuous)
            fixture.start(single)
            val raw = fixture.raw
            assertEquals(1, raw.starts)
            assertEquals(1, fixture.factory.clients.size)
            if (cancelSingle) single.stopLocation() else continuous.stopLocation()
            fixture.scheduler.runCurrent()
            assertEquals(0, raw.stops)
            val result = sample()
            raw.emit(result)
            fixture.scheduler.runCurrent()
            assertEquals(1, raw.stops)
            assertEquals(if (cancelSingle) 1 else 0, continuousResults.size)
            assertEquals(if (cancelSingle) 0 else 1, singleResults.size)
            continuous.onDestroy()
            single.onDestroy()
            fixture.scheduler.advanceBy(120_000L)
            assertEquals(1, raw.starts)
        }
    }

    @Test fun `last owner stop or destroy cancels future cycles and rejects queued or late callbacks`() {
        listOf(false, true).forEach { destroy ->
            val fixture = Fixture()
            var delivered = 0
            val owner = fixture.owner(option()) { delivered++ }
            fixture.start(owner)
            val raw = fixture.raw
            val oldCallback = requireNotNull(raw.listener)
            oldCallback.onLocationChanged(sample())
            if (destroy) owner.onDestroy() else owner.stopLocation()
            oldCallback.onLocationChanged(sample())
            fixture.scheduler.advanceBy(120_000L)
            assertEquals(0, delivered)
            assertEquals(1, raw.starts)
            assertEquals(1, raw.stops)

            var replacementDelivered = 0
            val replacement = fixture.owner(option()) { replacementDelivered++ }
            fixture.start(replacement)
            oldCallback.onLocationChanged(sample())
            fixture.scheduler.runCurrent()
            assertEquals(0, replacementDelivered)
            fixture.factory.clients.last().emit(sample())
            fixture.scheduler.runCurrent()
            assertEquals(1, replacementDelivered)
            replacement.onDestroy()
            if (!destroy) owner.onDestroy()
        }
    }

    @Test fun `shared delivery preserves source tick UTC time and accuracy`() {
        val fixture = Fixture()
        val received = mutableListOf<AMapLocation>()
        val continuous = fixture.owner(option()) { received += requireNotNull(it) }
        val single = fixture.owner(option(single = true)) { received += requireNotNull(it) }
        fixture.start(continuous)
        fixture.start(single)
        val source = sample()
        fixture.raw.emit(source)
        fixture.scheduler.runCurrent()
        assertEquals(2, received.size)
        received.forEach {
            assertEquals(SOURCE_TICK_NS, it.locationTickTime)
            assertEquals(SOURCE_UTC_MS, it.locationUtcTime)
            assertEquals(SOURCE_TIME_MS, it.time)
            assertEquals(SOURCE_ACCURACY_METERS, it.accuracy, 0f)
        }
        verify(exactly = 0) {
            source.locationTickTime = any()
            source.locationUtcTime = any()
            source.time = any()
            source.accuracy = any()
        }
        continuous.onDestroy()
        single.onDestroy()
    }

    @Test fun `joining single owner expires on its own absolute deadline without cancelling continuous flight`() {
        val fixture = Fixture()
        var continuousResults = 0
        var singleResults = 0
        val continuous = fixture.owner(option()) { continuousResults++ }
        fixture.start(continuous)
        fixture.scheduler.advanceBy(5_000L)
        val single = fixture.owner(option(single = true, timeoutMs = 5_000L)) { singleResults++ }
        fixture.start(single)
        assertEquals(1, fixture.raw.starts)
        fixture.scheduler.advanceBy(6_000L)
        assertEquals(0, singleResults)
        assertEquals(0, fixture.raw.stops)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, continuousResults)
        assertEquals(0, singleResults)
        continuous.onDestroy()
        single.onDestroy()
    }

    @Test fun `quiet waiting consumes single request budget and cannot start an expired request`() {
        val fixture = Fixture()
        val continuous = fixture.owner(option()) {}
        fixture.start(continuous)
        fixture.scheduler.advanceBy(1_000L)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        var singleResults = 0
        val single = fixture.owner(option(single = true, timeoutMs = 1_000L)) { singleResults++ }
        fixture.start(single)
        fixture.scheduler.advanceBy(1_000L)
        assertEquals(1, fixture.raw.starts)
        assertEquals(0, singleResults)
        fixture.scheduler.advanceTo(Fixture.INITIAL_TIME + 29_999L)
        assertEquals(1, fixture.raw.starts)
        fixture.scheduler.advanceBy(1L)
        assertEquals(2, fixture.raw.starts)
        continuous.onDestroy()
        single.onDestroy()
    }

    @Test fun `configuration delay and quiet waiting cannot renew a caller absolute deadline`() {
        val fixture = Fixture()
        val continuous = fixture.owner(option()) {}
        fixture.start(continuous)
        var singleResults = 0
        val deadline = fixture.scheduler.now() + 15_000L
        val single = fixture.owner(option(single = true, timeoutMs = 15_000L)) { singleResults++ }
        // Initialization and other work consume 14.5s before start receives the original deadline.
        fixture.scheduler.advanceBy(14_500L)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        single.startLocation(deadline)
        fixture.scheduler.runCurrent()
        fixture.scheduler.advanceBy(499L)
        assertEquals(1, fixture.raw.starts)
        fixture.scheduler.advanceBy(501L) // Quiet period ends 500ms after the caller expires.
        assertEquals(deadline + 500L, fixture.scheduler.now())
        assertEquals(1, fixture.raw.starts)
        assertEquals(1, fixture.raw.stops)
        assertEquals(0, singleResults)
        continuous.onDestroy()
        single.onDestroy()
    }

    @Test fun `account change isolates old flight and old owner cleanup from the new account`() {
        val fixture = Fixture()
        var oldResults = 0
        var newResults = 0
        val oldOwner = fixture.owner(option()) { oldResults++ }
        fixture.start(oldOwner)
        val oldNative = fixture.raw
        val oldCallback = requireNotNull(oldNative.listener)
        oldCallback.onLocationChanged(sample()) // Already queued when the account changes.
        fixture.account = LocationAccount(1, 1, 2)
        val newOwner = fixture.owner(option(single = true)) { newResults++ }
        fixture.start(newOwner)
        assertEquals(1, oldNative.stops)
        assertEquals(1, oldNative.destroys)
        assertEquals(1, fixture.factory.clients.size)
        assertEquals(0, oldResults)
        assertEquals(0, newResults)
        oldCallback.onLocationChanged(sample())
        fixture.scheduler.advanceBy(999L)
        assertEquals(1, fixture.factory.clients.size)
        assertEquals(0, newResults)
        fixture.scheduler.advanceBy(1L)
        assertEquals(2, fixture.factory.clients.size)
        val newNative = fixture.factory.clients.last()
        assertEquals(1, newNative.starts)
        oldCallback.onLocationChanged(sample())
        oldOwner.stopLocation()
        oldOwner.onDestroy()
        fixture.scheduler.runCurrent()
        assertEquals(0, newResults)
        assertEquals(0, newNative.stops)
        assertEquals(0, newNative.destroys)
        newNative.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(0, oldResults)
        assertEquals(1, newResults)
        newOwner.onDestroy()
    }

    @Test fun `equal account snapshot after credential refresh preserves active flight and continuous cycles`() {
        val fixture = Fixture()
        var continuousResults = 0
        var singleResults = 0
        val continuous = fixture.owner(option()) { continuousResults++ }
        fixture.start(continuous)
        val native = fixture.raw
        val previousAccount = requireNotNull(fixture.account)
        fixture.account = previousAccount.copy()
        assertNotSame(previousAccount, fixture.account)
        val single = fixture.owner(option(single = true)) { singleResults++ }
        fixture.start(single)
        assertEquals(1, fixture.factory.clients.size)
        assertEquals(1, native.starts)
        assertEquals(0, native.stops)
        assertEquals(0, native.destroys)
        native.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, continuousResults)
        assertEquals(1, singleResults)
        single.onDestroy()
        fixture.scheduler.advanceBy(30_000L)
        assertEquals(1, fixture.factory.clients.size)
        assertEquals(2, native.starts)
        assertEquals(0, native.destroys)
        continuous.onDestroy()
    }

    @Test fun `account change during fanout prevents delivery to the remaining old account recipients`() {
        val fixture = Fixture()
        var delivered = 0
        val callback: (AMapLocation?) -> Unit = {
            delivered++
            fixture.account = LocationAccount(1, 1, 2)
        }
        val first = fixture.owner(option(), callback)
        val second = fixture.owner(option(), callback)
        fixture.start(first)
        fixture.start(second)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, delivered)
        fixture.scheduler.advanceBy(120_000L)
        assertEquals(1, fixture.factory.clients.sumOf { it.starts })
        first.onDestroy()
        second.onDestroy()
    }

    @Test fun `cleanup failure stays latched across logout and replacement account admission`() {
        listOf<Pair<Boolean, Throwable>>(
            false to IllegalStateException("destroy failed"),
            true to IllegalStateException("stop failed"),
            false to LinkageError("destroy linkage failed"),
        ).forEach { (failStop, failure) ->
            val fixture = Fixture()
            val oldOwner = fixture.owner(option()) {}
            fixture.start(oldOwner)
            val native = fixture.raw
            if (failStop) native.stopFailure = failure
            else native.destroyFailure = failure
            fixture.account = null
            val logoutFailure = runCatching { fixture.provider.create("test-key") }.exceptionOrNull()
            assertEquals("amap_account_unavailable", logoutFailure?.message)
            assertEquals(1, native.stops)
            assertEquals(1, native.destroys)
            listOf(LocationAccount(1, 1, 2), null, LocationAccount(1, 1, 3)).forEach { account ->
                fixture.account = account
                val replacementFailure = runCatching { fixture.provider.create("test-key") }.exceptionOrNull()
                assertEquals(if (account == null) "amap_account_unavailable" else "amap_scheduler_unavailable",
                    replacementFailure?.message)
            }
            oldOwner.onDestroy()
            fixture.scheduler.advanceBy(120_000L)
            assertEquals(1, fixture.factory.clients.size)
            assertEquals(1, native.starts)
        }
    }

    @Test fun `rejected start before native creation recovers only after all owners close`() {
        listOf(false, true).forEach { failFactory ->
            val fixture = Fixture()
            val errors = mutableListOf<Int?>()
            val first = fixture.owner(option()) { errors += it?.errorCode }
            val second = fixture.owner(option(single = true)) { errors += it?.errorCode }
            if (failFactory) fixture.factory.createFailure = IllegalStateException("initialization unavailable")
            else fixture.startFailure = SecurityException("permission unavailable")
            first.startLocation()
            second.startLocation()
            fixture.scheduler.runCurrent()
            assertEquals(2, errors.size)
            assertTrue(fixture.factory.clients.isEmpty())

            fixture.factory.createFailure = null
            fixture.startFailure = null
            fixture.scheduler.advanceBy(120_000L)
            assertTrue(fixture.factory.clients.isEmpty())
            first.onDestroy()
            assertEquals("amap_scheduler_unavailable",
                runCatching { fixture.provider.create("test-key") }.exceptionOrNull()?.message)
            second.onDestroy()

            var delivered = 0
            val replacement = fixture.owner(option()) { delivered++ }
            fixture.start(replacement)
            fixture.raw.emit(sample())
            fixture.scheduler.runCurrent()
            assertEquals(1, delivered)
            assertEquals(1, fixture.raw.starts)
            replacement.onDestroy()
        }
    }

    @Test fun `account replacement can recover a rejected start without native ownership`() {
        val fixture = Fixture()
        val oldOwner = fixture.owner(option()) {}
        fixture.startFailure = SecurityException("permission unavailable")
        fixture.start(oldOwner)
        assertTrue(fixture.factory.clients.isEmpty())

        fixture.startFailure = null
        fixture.account = LocationAccount(1, 1, 2)
        val replacement = fixture.owner(option()) {}
        fixture.start(replacement)
        oldOwner.onDestroy()
        assertEquals(1, fixture.raw.starts)
        assertEquals(0, fixture.raw.stops)
        replacement.onDestroy()
    }

    @Test fun `stop failure ends all owners and prevents automatic native restart`() {
        val fixture = Fixture()
        val failures = mutableListOf<Int?>()
        val continuous = fixture.owner(option()) { failures += it?.errorCode }
        val single = fixture.owner(option(single = true)) { failures += it?.errorCode }
        fixture.start(continuous)
        fixture.start(single)
        fixture.raw.stopFailure = IllegalStateException("stop failed")
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(listOf(AMapLocation.ERROR_CODE_FAILURE_INIT, AMapLocation.ERROR_CODE_FAILURE_INIT), failures)
        fixture.scheduler.advanceBy(120_000L)
        assertEquals(1, fixture.factory.clients.sumOf { it.starts })
        assertEquals(1, fixture.raw.stops)
        continuous.onDestroy()
        single.onDestroy()
        assertEquals("amap_scheduler_unavailable",
            runCatching { fixture.provider.create("test-key") }.exceptionOrNull()?.message)
    }

    @Test fun `interval updates affect next cycle without restarting the active flight`() {
        val fixture = Fixture()
        val owner = fixture.owner(option()) {}
        fixture.start(owner)
        val raw = fixture.raw
        val initialOptionCount = raw.options.size
        fixture.scheduler.advanceBy(500L)
        owner.setLocationOption(option(intervalMs = 3_000L))
        owner.setLocationOption(option(intervalMs = 30_000L))
        fixture.scheduler.runCurrent()
        assertEquals(1, raw.starts)
        assertEquals(0, raw.stops)
        assertEquals(initialOptionCount, raw.options.size)
        raw.emit(sample())
        fixture.scheduler.runCurrent()
        fixture.scheduler.advanceTo(Fixture.INITIAL_TIME + 29_999L)
        assertEquals(1, raw.starts)
        fixture.scheduler.advanceBy(1L)
        assertEquals(2, raw.starts)
        owner.setLocationOption(option(intervalMs = 3_000L))
        fixture.scheduler.advanceBy(100L)
        raw.emit(sample())
        fixture.scheduler.runCurrent()
        fixture.scheduler.advanceTo(Fixture.INITIAL_TIME + 32_999L)
        assertEquals(2, raw.starts)
        fixture.scheduler.advanceBy(1L)
        assertEquals(3, raw.starts)
        owner.onDestroy()
    }

    @Test fun `fanout rechecks owner state after an earlier callback cancels the other owner`() {
        val fixture = Fixture()
        var delivered = 0
        lateinit var first: AmapClientHandle
        lateinit var second: AmapClientHandle
        first = fixture.owner(option()) { delivered++; second.stopLocation() }
        second = fixture.owner(option()) { delivered++; first.stopLocation() }
        fixture.start(first)
        fixture.start(second)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, delivered)
        first.onDestroy()
        second.onDestroy()
    }

    @Test fun `native deadline stops and waits before retry without inventing a location error`() {
        val fixture = Fixture()
        var delivered = 0
        val owner = fixture.owner(option()) { delivered++ }
        fixture.start(owner)
        val oldCallback = requireNotNull(fixture.raw.listener)
        fixture.scheduler.advanceBy(30_000L)
        assertEquals(1, fixture.raw.stops)
        assertEquals(1, fixture.raw.starts)
        assertEquals(0, delivered)
        fixture.scheduler.advanceBy(999L)
        assertEquals(1, fixture.raw.starts)
        fixture.scheduler.advanceBy(1L)
        assertEquals(2, fixture.raw.starts)
        oldCallback.onLocationChanged(sample())
        fixture.scheduler.runCurrent()
        assertEquals(0, delivered)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(1, delivered)
        owner.onDestroy()
    }

    @Test fun `stop latency cannot deliver a single result past its original deadline`() {
        val fixture = Fixture()
        var delivered = 0
        val owner = fixture.owner(option(single = true, timeoutMs = 5_000L)) { delivered++ }
        fixture.start(owner)
        fixture.raw.onStop = { fixture.scheduler.elapseWithoutRunning(2L) }
        fixture.scheduler.advanceBy(4_999L)
        fixture.raw.emit(sample())
        fixture.scheduler.runCurrent()
        assertEquals(Fixture.INITIAL_TIME + 5_001L, fixture.scheduler.now())
        assertEquals(1, fixture.raw.stops)
        assertEquals(0, delivered)
        owner.onDestroy()
    }

    private fun option(
        single: Boolean = false,
        intervalMs: Long = 30_000L,
        timeoutMs: Long = 30_000L,
    ) = AMapLocationClientOption().apply {
        isOnceLocation = single
        interval = intervalMs
        httpTimeOut = timeoutMs
        isNeedAddress = false
        isLocationCacheEnable = false
    }

    private fun sample(): AMapLocation = mockk<AMapLocation>(relaxed = true).also { location ->
        every { location.errorCode } returns 0
        every { location.locationTickTime } returns SOURCE_TICK_NS
        every { location.locationUtcTime } returns SOURCE_UTC_MS
        every { location.time } returns SOURCE_TIME_MS
        every { location.accuracy } returns SOURCE_ACCURACY_METERS
        every { location.clone() } returns location
    }

    private class Fixture {
        val scheduler = Scheduler()
        val factory = Factory()
        var account: LocationAccount? = LocationAccount(1, 1, 1)
        var startFailure: Exception? = null
        val provider = SerializedAmapClientProvider(
            rawFactory = factory,
            scheduler = scheduler,
            checkStartAllowed = { startFailure?.let { throw it } },
            failureLocation = { code -> mockk<AMapLocation> { every { errorCode } returns code } },
            currentAccount = { account },
        )
        val raw get() = factory.clients.single()

        fun owner(option: AMapLocationClientOption, callback: (AMapLocation?) -> Unit): AmapClientHandle =
            provider.create("test-key").apply {
                setLocationOption(option)
                setLocationListener { callback(it) }
            }

        fun start(owner: AmapClientHandle) {
            owner.startLocation()
            scheduler.runCurrent()
        }

        companion object { const val INITIAL_TIME = 100_000L }
    }

    private class Factory : AmapClientProvider {
        val clients = mutableListOf<NativeClient>()
        var createFailure: Exception? = null
        override fun create(apiKey: String): AmapClientHandle {
            createFailure?.let { throw it }
            return NativeClient().also { clients += it }
        }
    }

    private class NativeClient : AmapClientHandle {
        val options = mutableListOf<AMapLocationClientOption>()
        var listener: AMapLocationListener? = null
        var starts = 0
        var stops = 0
        var destroys = 0
        var stopFailure: Throwable? = null
        var destroyFailure: Throwable? = null
        var onStop: () -> Unit = {}
        override fun setLocationOption(option: AMapLocationClientOption) { options += option }
        override fun setLocationListener(listener: AMapLocationListener) { this.listener = listener }
        override fun unRegisterLocationListener(listener: AMapLocationListener) {
            if (this.listener === listener) this.listener = null
        }
        override fun startLocation() { starts++ }
        override fun stopLocation() {
            stops++
            onStop()
            stopFailure?.let { throw it }
        }
        override fun onDestroy() {
            destroys++
            destroyFailure?.let { throw it }
        }
        fun emit(location: AMapLocation?) { requireNotNull(listener).onLocationChanged(location) }
    }

    private class Scheduler : AmapCycleScheduler {
        data class Task(val at: Long, val sequence: Long, val runnable: Runnable)
        private val tasks = PriorityQueue(compareBy<Task> { it.at }.thenBy { it.sequence })
        private var time = Fixture.INITIAL_TIME
        private var sequence = 0L
        override fun now() = time
        override fun isMainThread() = true
        override fun post(delayMs: Long, task: Runnable) {
            tasks += Task(time + delayMs.coerceAtLeast(0L), sequence++, task)
        }
        override fun remove(task: Runnable) { tasks.removeAll { it.runnable === task } }
        fun runCurrent() = advanceTo(time)
        fun advanceBy(durationMs: Long) = advanceTo(time + durationMs)
        fun elapseWithoutRunning(durationMs: Long) { time += durationMs }
        fun advanceTo(target: Long) {
            while (tasks.peek()?.at?.let { it <= target } == true) {
                val task = tasks.remove()
                time = maxOf(time, task.at)
                task.runnable.run()
            }
            time = maxOf(time, target)
        }
    }

    companion object {
        private const val SOURCE_TICK_NS = 98_000_000_000L
        private const val SOURCE_UTC_MS = 1_700_000_000_000L
        private const val SOURCE_TIME_MS = SOURCE_UTC_MS + 1_000L
        private const val SOURCE_ACCURACY_METERS = 17.5f
    }
}
