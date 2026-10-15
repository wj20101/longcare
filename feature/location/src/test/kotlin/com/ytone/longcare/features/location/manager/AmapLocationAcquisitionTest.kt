package com.ytone.longcare.features.location.manager

import android.os.SystemClock
import com.amap.api.location.*
import com.ytone.longcare.domain.location.*
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.common.utils.KLogger
import kotlinx.coroutines.flow.collect
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmapLocationAcquisitionTest {
    private val dispatcher = StandardTestDispatcher()
    private val listeners = mutableListOf<AMapLocationListener>()
    private val options = mutableListOf<AMapLocationClientOption>()
    private val client = mockk<AMapLocationClient>(relaxed = true)
    private lateinit var manager: ContinuousAmapLocationManager
    private lateinit var singleSource: SingleAmapLocationSource
    private lateinit var factory: AmapLocationClientFactory

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        Dispatchers.setMain(dispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { dispatcher.scheduler.currentTime + 1_000 }
        every { client.setLocationListener(capture(listeners)) } just Runs
        every { client.setLocationOption(capture(options)) } just Runs
        every { client.startLocation() } just Runs
        every { client.stopLocation() } just Runs
        every { client.onDestroy() } just Runs
        every { client.unRegisterLocationListener(any()) } just Runs
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackError(any(), any(), any()) } just Runs
        val key = mockk<AmapApiKeyProvider>()
        coEvery { key.getAmapApiKey() } returns "test-key"
        factory = mockk<AmapLocationClientFactory> { every { create(any()) } returns SdkAmapClientHandle(client) }
        manager = ContinuousAmapLocationManager(factory, key)
        singleSource = SingleAmapLocationSource(factory, key)
    }

    @After fun cleanup() { Dispatchers.resetMain(); unmockkAll() }

    @Test fun `single config metadata duplicate callback and cleanup are real SDK boundary behavior`() = runTest(dispatcher) {
        val result = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        val option = options.single()
        assertFalse(option.isLocationCacheEnable)
        assertFalse(option.isMockEnable)
        assertFalse(option.isNeedAddress)
        assertTrue(option.isOnceLocation)
        assertTrue(option.isOnceLocationLatest)
        assertEquals(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy, option.locationMode)
        val callback = listeners.single()
        val raw = sample()
        callback.onLocationChanged(raw)
        callback.onLocationChanged(raw)
        val success = result.await() as LocationAcquisition.Success
        assertEquals(raw.time, success.location.locationTime)
        assertEquals(raw.accuracy, success.location.accuracy)
        assertEquals(raw.latitude, success.location.latitude, 0.0)
        assertEquals(raw.longitude, success.location.longitude, 0.0)
        assertEquals(raw.locationType, success.location.locationType)
        assertEquals(raw.trustedLevel, success.location.trustedLevel)
        assertEquals("GCJ02", success.location.coordType)
        verify(exactly = 1) { client.onDestroy() }
        verify(exactly = 1) { client.unRegisterLocationListener(callback) }
    }

    @Test fun `cancel cleans own client and ignores late callback without starting retry`() = runTest(dispatcher) {
        val job = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        job.cancelAndJoin()
        listeners.single().onLocationChanged(sample())
        runCurrent()
        assertEquals(1, options.size)
        verify(exactly = 1) { client.stopLocation() }
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `SDK failure retains raw result and error details for business diagnostics`() = runTest(dispatcher) {
        val raw = sample()
        every { raw.errorCode } returns AMapLocation.ERROR_CODE_FAILURE_CONNECTION
        every { raw.errorInfo } returns "network unavailable"
        val request = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        listeners.single().onLocationChanged(raw)
        val failure = request.await() as LocationAcquisition.Failure
        assertEquals(LocationFailure.NETWORK, failure.reason)
        val snapshot = requireNotNull(failure.location)
        assertEquals(raw.longitude, snapshot.longitude, 0.0)
        assertEquals(raw.latitude, snapshot.latitude, 0.0)
        assertEquals(raw.errorCode, snapshot.errorCode)
        assertEquals("network unavailable", snapshot.errorInfo)
    }

    @Test fun `quality retry preserves final SDK sample instead of dropping diagnostics`() = runTest(dispatcher) {
        val request = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        val stale = sample().also { every { it.time } returns System.currentTimeMillis() - 60_000 }
        listeners.last().onLocationChanged(stale)
        runCurrent()
        assertEquals(2, listeners.size)
        val second = sample().also { every { it.trustedLevel } returns AMapLocation.TRUSTED_LEVEL_LOW }
        listeners.last().onLocationChanged(second)
        val failure = request.await() as LocationAcquisition.Failure
        assertEquals(LocationFailure.QUALITY, failure.reason)
        assertEquals(AMapLocation.TRUSTED_LEVEL_LOW, failure.location!!.trustedLevel)
        assertEquals(second.time, failure.location!!.locationTime)
    }

    @Test fun `timeout destroys isolated client`() = runTest(dispatcher) {
        val job = async { singleSource.acquireCurrentLocation() }
        advanceUntilIdle()
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), job.await())
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `service order keeps one continuous client while awaiting acceptable precision`() = runTest(dispatcher) {
        val request = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.last().onLocationChanged(sample().also { every { it.accuracy } returns 2_000f })
        runCurrent()
        assertEquals(1, listeners.size)
        listeners.last().onLocationChanged(sample().also { every { it.accuracy } returns 200f })
        val success = request.await() as LocationAcquisition.Success
        assertEquals(200f, success.location.accuracy)
        assertTrue(success.location.requestId.isNotBlank())
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `service order precision rejection survives retry timeout with original evidence`() = runTest(dispatcher) {
        val request = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.last().onLocationChanged(sample().also { every { it.accuracy } returns 800f })
        advanceUntilIdle()
        val failure = request.await() as LocationAcquisition.Failure
        assertEquals(LocationFailure.PRECISION, failure.reason)
        assertEquals(800f, requireNotNull(failure.location).accuracy)
        assertEquals(1, listeners.size)
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `service order retries network failure but never retries simulated locations`() = runTest(dispatcher) {
        val request = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.last().onLocationChanged(sample().also { every { it.errorCode } returns 4 })
        runCurrent()
        listeners.last().onLocationChanged(sample())
        assertTrue(request.await() is LocationAcquisition.Success)
        val simulated = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.last().onLocationChanged(sample().also { every { it.isMock } returns true })
        assertEquals(LocationFailure.QUALITY, (simulated.await() as LocationAcquisition.Failure).reason)
        assertEquals(2, listeners.size)
    }

    @Test fun `acquisition and cleanup failures retain their original diagnostic exceptions`() = runTest(dispatcher) {
        val acquisitionError = IllegalStateException("Location start failed")
        val cleanupError = IllegalArgumentException("Location cleanup failed")
        every { client.startLocation() } throws acquisitionError
        every { client.stopLocation() } throws cleanupError

        assertEquals(LocationAcquisition.Failure(LocationFailure.UNAVAILABLE), singleSource.acquireCurrentLocation())

        verify(exactly = 1) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL, acquisitionError, any())
            LocationEventTracker.trackError(LocationEventTracker.EventType.AMAP_SINGLE_LOCATION_FAIL, cleanupError, any())
        }
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `single completion and cancellation never stop continuous client`() = runTest(dispatcher) {
        val continuous = mockk<AMapLocationClient>(relaxed = true)
        val continuousListener = slot<AMapLocationListener>()
        val continuousOption = slot<AMapLocationClientOption>()
        every { continuous.setLocationListener(capture(continuousListener)) } just Runs
        every { continuous.setLocationOption(capture(continuousOption)) } just Runs
        every { factory.create(any()) } returnsMany listOf(continuous, client, client).map(::SdkAmapClientHandle)
        val samples = mutableListOf<com.ytone.longcare.model.LocationResult>()
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").collect { samples += it } }
        runCurrent()
        assertFalse(continuousOption.captured.isLocationCacheEnable)
        assertFalse(continuousOption.captured.isOnceLocation)
        assertEquals(30_000, continuousOption.captured.interval)
        val single = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        listeners.single().onLocationChanged(sample())
        assertTrue(single.await() is LocationAcquisition.Success)
        val cancelled = async { singleSource.acquireCurrentLocation() }
        runCurrent()
        cancelled.cancelAndJoin()
        verify(exactly = 0) { continuous.stopLocation() }
        verify(exactly = 0) { continuous.onDestroy() }
        continuousListener.captured.onLocationChanged(sample())
        runCurrent()
        assertEquals(1, samples.size)
        collector.cancelAndJoin()
        verify(exactly = 1) { continuous.stopLocation() }
    }

    @Test fun `new continuous collection cannot receive a delayed result from the previous SDK client`() = runTest(dispatcher) {
        val previous = mockk<AMapLocationClient>(relaxed = true)
        val next = mockk<AMapLocationClient>(relaxed = true)
        var previousActiveListener: AMapLocationListener? = null
        val nextListener = slot<AMapLocationListener>()
        every { previous.setLocationListener(any()) } answers { previousActiveListener = firstArg() }
        every { previous.unRegisterLocationListener(any()) } answers {
            if (previousActiveListener === firstArg<AMapLocationListener>()) previousActiveListener = null
        }
        every { next.setLocationListener(capture(nextListener)) } just Runs
        every { factory.create(any()) } returnsMany listOf(previous, next).map(::SdkAmapClientHandle)

        val first = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").collect() }
        runCurrent()
        val oldCallback = requireNotNull(previousActiveListener)
        first.cancelAndJoin()
        val received = mutableListOf<com.ytone.longcare.model.LocationResult>()
        val second = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").collect { received += it } }
        runCurrent()

        // 模拟旧客户端的在途定位完成；时间仍然新鲜，不能依赖 15 秒规则来隔离订单。
        val delayed = sample().also { every { it.time } returns System.currentTimeMillis() }
        previousActiveListener?.onLocationChanged(delayed)
        oldCallback.onLocationChanged(delayed)
        runCurrent()
        assertTrue("Previous SDK work leaked into the next collection", received.isEmpty())
        verify(exactly = 2) { factory.create(any()) }
        verify(exactly = 1) { previous.onDestroy() }

        nextListener.captured.onLocationChanged(sample())
        runCurrent()
        assertEquals(1, received.size)
        second.cancelAndJoin()
        verify(exactly = 1) { next.onDestroy() }
    }

    @Test fun `continuous cleanup failures still stop and destroy own client`() = runTest(dispatcher) {
        val unregisterError = IllegalStateException("unregister failed")
        val stopError = IllegalStateException("stop failed")
        every { client.unRegisterLocationListener(any()) } throws unregisterError
        every { client.stopLocation() } throws stopError
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").collect() }
        runCurrent()
        collector.cancelAndJoin()
        verify(exactly = 1) { client.stopLocation() }
        verify(exactly = 1) { client.onDestroy() }
        listOf(unregisterError, stopError).forEach { error ->
            verify(exactly = 1) {
                LocationEventTracker.trackError(LocationEventTracker.EventType.CLIENT_CLEANUP_ERROR, error, any())
            }
        }
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `cancel closes continuous flow and ignores late success and error callbacks`() = runTest(dispatcher) {
        val received = mutableListOf<com.ytone.longcare.model.LocationResult>()
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").collect { received += it } }
        runCurrent()
        val listener = listeners.single()
        collector.cancelAndJoin()
        listener.onLocationChanged(sample())
        listener.onLocationChanged(sample().also { every { it.errorCode } returns 4 })
        runCurrent()
        assertTrue(collector.isCompleted)
        assertTrue(received.isEmpty())
        verify(exactly = 1) { client.onDestroy() }
        verify(exactly = 0) {
            LocationEventTracker.trackError(LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR, any(), any())
        }
    }

    @Test fun `cancel while key lookup is pending never creates a late continuous client`() = runTest(dispatcher) {
        val keyResponse = CompletableDeferred<String>()
        val keyProvider = mockk<AmapApiKeyProvider>()
        coEvery { keyProvider.getAmapApiKey() } coAnswers {
            withContext(NonCancellable) { keyResponse.await() }
        }
        val isolatedManager = ContinuousAmapLocationManager(factory, keyProvider)
        val collector = backgroundScope.launch { isolatedManager.startContinuousLocation(owner = "test-owner").collect() }
        runCurrent()
        collector.cancel()
        keyResponse.complete("test-key")
        collector.join()
        verify(exactly = 0) { factory.create(any()) }
    }

    @Test fun `service actions temporarily raise existing sampling then restore 30 second cadence`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        assertEquals(30_000, options.last().interval)
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertEquals(3_000, options.last().interval)
        assertFalse(options.last().isOnceLocation)
        assertTrue(options.last().isOnceLocationLatest)
        assertEquals(20_000, options.last().httpTimeOut)
        listeners.single().onLocationChanged(sample().also { every { it.trustedLevel } returns 2 })
        assertTrue(action.await() is LocationAcquisition.Success)
        assertEquals(30_000, options.last().interval)
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `acceptable warm point returns without changing the running SDK cadence`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        val raw = sample().also {
            every { it.accuracy } returns 200f
            every { it.trustedLevel } returns AMapLocation.TRUSTED_LEVEL_NORMAL
        }
        listeners.single().onLocationChanged(raw)

        val first = manager.acquireServiceOrderLocation() as LocationAcquisition.Success
        val second = manager.acquireServiceOrderLocation() as LocationAcquisition.Success

        assertEquals(raw.latitude, first.location.latitude, 0.0)
        assertEquals(raw.time, first.location.locationTime)
        assertNotEquals(first.location.requestId, second.location.requestId)
        assertEquals(listOf(30_000L), options.map { it.interval })
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
    }

    @Test fun `joining and leaving an active action does not reapply identical SDK options`() = runTest(dispatcher) {
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        collector.cancelAndJoin()

        assertEquals(listOf(3_000L), options.map { it.interval })
        verify(exactly = 0) { client.stopLocation() }
        listeners.single().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `cancelling an action restores reporting cadence and keeps its client alive`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()

        action.cancelAndJoin()

        assertEquals(listOf(30_000L, 3_000L, 30_000L), options.map { it.interval })
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
    }

    @Test fun `completed standalone action cannot supply warm replay to a new client`() = runTest(dispatcher) {
        val first = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.single().onLocationChanged(sample())
        assertTrue(first.await() is LocationAcquisition.Success)
        verify(exactly = 1) { client.onDestroy() }

        val second = async { manager.acquireServiceOrderLocation() }
        runCurrent()

        assertFalse(second.isCompleted)
        verify(exactly = 2) { factory.create(any()) }
        listeners.last().onLocationChanged(sample())
        assertTrue(second.await() is LocationAcquisition.Success)
    }

    @Test fun `replayed mock point is ignored and fresh valid callback can complete the next action`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        listeners.single().onLocationChanged(sample().also { every { it.isMock } returns true })

        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()

        assertFalse("A previous mock result must not fail the new action", action.isCompleted)
        listeners.single().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        collector.cancelAndJoin()
    }

    @Test fun `replayed poor precision never becomes evidence for a new action timeout`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        listeners.single().onLocationChanged(sample().also { every { it.accuracy } returns 2_000f })

        val action = async { manager.acquireServiceOrderLocation() }
        advanceUntilIdle()

        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), action.await())
        assertEquals(listOf(30_000L, 3_000L, 30_000L), options.map { it.interval })
        collector.cancelAndJoin()
    }

    @Test fun `stale replay never satisfies a new service action`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        listeners.single().onLocationChanged(sample().also { every { it.time } returns System.currentTimeMillis() - 10_000 })
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertFalse(action.isCompleted)
        listeners.single().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        collector.cancelAndJoin()
    }

    @Test fun `new action ignores previous terminal failure and waits for its own callback`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        val previousFailure = sample().also {
            every { it.errorCode } returns AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION
        }
        listeners.single().onLocationChanged(previousFailure)
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertFalse("A replayed failure must not terminate a new action", action.isCompleted)
        listeners.single().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        collector.cancelAndJoin()
    }

    @Test fun `terminal failure from current action still returns immediately`() = runTest(dispatcher) {
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.single().onLocationChanged(sample().also {
            every { it.errorCode } returns AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION
        })
        assertEquals(LocationFailure.PERMISSION, (action.await() as LocationAcquisition.Failure).reason)
        assertEquals(0, currentTime)
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `only 90 seconds without raw callbacks rebuilds owned SDK sampling`() = runTest(dispatcher) {
        var elapsed = 10_000L
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { elapsed }
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "order-1").collect() }
        runCurrent()
        elapsed += 89_999
        assertFalse(manager.recoverStalledSampling("order-1"))
        elapsed += 1
        assertFalse(manager.recoverStalledSampling("other-owner"))
        assertTrue(manager.recoverStalledSampling("order-1"))
        verify(exactly = 2) { factory.create(any()) }
        verify(exactly = 1) { client.onDestroy() }
        elapsed += 30_000
        // Network failure is a raw SDK callback, not a dead client.
        listeners.last().onLocationChanged(sample().also { every { it.errorCode } returns 4 })
        elapsed += 60_000
        assertFalse(manager.recoverStalledSampling("order-1"))
        collector.cancelAndJoin()
    }

    private fun sample(): AMapLocation = mockk {
        every { errorCode } returns 0
        every { errorInfo } returns "success"
        every { latitude } returns 31.23456789012345
        every { longitude } returns 121.98765432109876
        every { provider } returns "network"
        every { accuracy } returns 8f
        every { coordType } returns "GCJ02"
        every { locationType } returns AMapLocation.LOCATION_TYPE_WIFI
        every { trustedLevel } returns AMapLocation.TRUSTED_LEVEL_HIGH
        every { time } returns System.currentTimeMillis() - 2_000
        every { isMock } returns false
        every { isFixLastLocation } returns false
    }
}
