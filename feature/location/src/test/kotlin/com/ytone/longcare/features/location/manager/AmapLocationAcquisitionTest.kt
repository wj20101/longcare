package com.ytone.longcare.features.location.manager

import android.net.ConnectivityManager
import android.os.SystemClock
import com.amap.api.location.*
import com.ytone.longcare.domain.location.*
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.features.location.core.AndroidLocationRuntimeReadiness
import com.ytone.longcare.features.location.reporting.LocationClock
import com.ytone.longcare.features.location.reporting.LocationSampleEvaluator
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.diagnostics.DiagnosticException
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.KLogger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
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
    // Fixtures include two-second-old valid fixes; those fixes must exist after this boot.
    private val initialElapsedRealtime = 100_000L
    private val listeners = mutableListOf<AMapLocationListener>()
    private val options = mutableListOf<AMapLocationClientOption>()
    private val actionDiagnostics = mutableListOf<Map<String, Any?>>()
    private val client = mockk<AMapLocationClient>(relaxed = true)
    private lateinit var manager: ContinuousAmapLocationManager
    private lateinit var singleSource: SingleAmapLocationSource
    private lateinit var factory: AmapLocationClientFactory
    private val runtimeReadiness = mockk<AndroidLocationRuntimeReadiness> {
        every { diagnosticSnapshot() } returns emptyMap()
        every { isAppVisible() } returns true
    }

    @Before fun setup() {
        KLogger.updateConfig { enabled = false }
        Dispatchers.setMain(dispatcher)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } answers { dispatcher.scheduler.currentTime + initialElapsedRealtime }
        every { client.setLocationListener(capture(listeners)) } just Runs
        every { client.setLocationOption(capture(options)) } just Runs
        every { client.startLocation() } just Runs
        every { client.stopLocation() } just Runs
        every { client.onDestroy() } just Runs
        every { client.unRegisterLocationListener(any()) } just Runs
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackError(any(), any(), any()) } just Runs
        every { LocationEventTracker.trackEvent(any(), any(), any(), any()) } answers {
            if (firstArg<LocationEventTracker.EventType>() == LocationEventTracker.EventType.SERVICE_ACTION_RESULT)
                actionDiagnostics += secondArg<Map<String, Any?>>() + ("remote" to arg<Boolean>(2))
        }
        val key = mockk<AmapApiKeyProvider>()
        coEvery { key.getAmapApiKey() } returns "test-key"
        factory = mockk<AmapLocationClientFactory> { every { create(any()) } returns SdkAmapClientHandle(client) }
        manager = ContinuousAmapLocationManager(factory, key, runtimeReadiness)
        singleSource = SingleAmapLocationSource(factory, key)
    }

    @After fun cleanup() { Dispatchers.resetMain(); unmockkAll() }

    @Test fun `source tick is converted from nanoseconds with one received clock snapshot`() {
        val raw = sample().also { every { it.locationTickTime } returns 8_123_456_789L }
        var receivedClockReads = 0
        every { SystemClock.elapsedRealtime() } answers { 12_345L + receivedClockReads++ }
        val before = System.currentTimeMillis()
        val converted = raw.toSample("source-clock")
        val after = System.currentTimeMillis()

        assertEquals(8_123L, converted.sourceElapsedRealtime)
        assertEquals(12_345L, converted.receivedElapsedRealtime)
        assertEquals(1, receivedClockReads)
        assertTrue(converted.receivedAt in before..after)
        verify(exactly = 1) { raw.locationTickTime }
    }

    @Test fun `unknown SDK source tick stays unknown instead of using callback time`() {
        val raw = sample()
        listOf(0L, -1L).forEach { unknownTick ->
            every { raw.locationTickTime } returns unknownTick
            assertEquals(0L, raw.toSample("unknown-source-clock").sourceElapsedRealtime)
        }
    }

    @Test fun `runtime packs deep and light idle separately and preserves unknown values`() {
        assertEquals("idle=?/?,screen=?,net=?,validated=?,exempt=?,save=?,bg=?,ds=?,lps=?", manager.diagnosticSnapshot()["runtime"])
        every { runtimeReadiness.diagnosticSnapshot() } returns mapOf(
            "deviceIdle" to false, "deviceLightIdle" to null, "screenOn" to false,
            "networkAvailable" to true, "networkValidated" to true, "batteryExempt" to false, "powerSave" to true,
        )
        assertEquals("idle=0/?,screen=0,net=1,validated=1,exempt=0,save=1,bg=?,ds=?,lps=?", manager.diagnosticSnapshot()["runtime"])
        every { runtimeReadiness.diagnosticSnapshot() } returns mapOf("deviceIdle" to false, "deviceLightIdle" to true)
        assertTrue(manager.diagnosticSnapshot()["runtime"].toString().startsWith("idle=0/1,"))
        every { runtimeReadiness.diagnosticSnapshot() } returns mapOf("deviceIdle" to true, "deviceLightIdle" to false)
        assertTrue(manager.diagnosticSnapshot()["runtime"].toString().startsWith("idle=1/0,"))
    }

    @Test fun `runtime reports supported background policies without treating validated network as UID reachability`() {
        for (status in listOf(ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED,
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_WHITELISTED,
            ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED)) {
            every { runtimeReadiness.diagnosticSnapshot() } returns mapOf(
                "networkValidated" to true, "backgroundRestricted" to true,
                "restrictBackgroundStatus" to status, "lowPowerStandbyEnabled" to true,
            )
            val runtime = manager.diagnosticSnapshot()["runtime"].toString()
            assertTrue(runtime.contains("validated=1,"))
            assertTrue(runtime.endsWith("bg=1,ds=$status,lps=1"))
        }
        for (status in listOf(null, -1, 0, 4, "3", 1L)) {
            every { runtimeReadiness.diagnosticSnapshot() } returns mapOf(
                "restrictBackgroundStatus" to status, "backgroundRestricted" to false,
                "lowPowerStandbyEnabled" to false,
            )
            assertTrue(manager.diagnosticSnapshot()["runtime"].toString().endsWith("bg=0,ds=?,lps=0"))
        }
    }

    @Test fun `SDK diagnostic counter precedes flow conflation and ignores released client callbacks`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val callback = listeners.single()
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().contains("count=0,null=0,ageMs=?"))
        repeat(3) { callback.onLocationChanged(sample()) }
        callback.onLocationChanged(null)
        advanceTimeBy(2_000)
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().contains("count=4,null=1,ageMs=2000"))
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().contains("intervalMs=30000"))
        collector.cancelAndJoin()
        callback.onLocationChanged(sample())
        assertNull(manager.diagnosticSnapshot()["clientId"])
        assertNull(manager.diagnosticSnapshot()["sdkState"])
    }

    @Test fun `action diagnostics retain source age when fresh callback reuses an old source`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val sourceElapsedRealtime = SystemClock.elapsedRealtime()
        advanceTimeBy(10_000)
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        listeners.single().onLocationChanged(sample().also {
            every { it.locationTickTime } returns TimeUnit.MILLISECONDS.toNanos(sourceElapsedRealtime)
        })
        runCurrent()
        advanceTimeBy(30_000)
        runCurrent()
        assertTrue(action.await() is LocationAcquisition.Failure)
        val fields = actionDiagnostics.single()
        assertTrue(fields["time"].toString().contains("source=10000"))
        assertTrue(fields["time"].toString().contains("elapsed=30000"))
        assertEquals(30_000L, fields["rawGapMs"])
        assertTrue(fields["sdkState"].toString().contains("intervalMs=3000"))
        assertEquals(30_000L, options.last().interval)
        collector.cancelAndJoin()
    }

    @Test fun `SDK refreshed reused time cannot bypass action cutoff or reporting age`() {
        val now = System.currentTimeMillis()
        listOf(AMapLocation.LOCATION_TYPE_SAME_REQ, AMapLocation.LOCATION_TYPE_FIX_CACHE).forEach { type ->
            val raw = sample().also {
                every { it.locationType } returns type
                every { it.time } returns now
                every { it.locationUtcTime } returns now - 10_000L
            }
            val beforeAction = raw.toSample("action")
            assertEquals(now - 10_000L, beforeAction.locationTime)
            assertEquals(LocationQuality.Rejection.ORDER, ServiceOrderLocationQuality.rejection(
                beforeAction, beforeAction.receivedAt, beforeAction.receivedElapsedRealtime,
                notBeforeElapsedRealtime = beforeAction.receivedElapsedRealtime - 3_000L,
                maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
            ))

            every { raw.locationUtcTime } returns now - ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS - 1L
            val expired = raw.toSample("reporting")
            assertEquals(LocationQuality.Rejection.STALE, ServiceOrderLocationQuality.rejection(
                expired, expired.receivedAt, expired.receivedElapsedRealtime,
                maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
            ))
        }
    }

    @Test fun `fresh same request source UTC remains eligible while fix cache stays excluded`() {
        val now = System.currentTimeMillis()
        val raw = sample().also {
            every { it.locationType } returns AMapLocation.LOCATION_TYPE_SAME_REQ
            every { it.time } returns now
            every { it.locationUtcTime } returns now - 1_000L
        }
        val fresh = raw.toSample("action")
        assertEquals(now - 1_000L, fresh.locationTime)
        assertNull(ServiceOrderLocationQuality.rejection(
            fresh, fresh.receivedAt, fresh.receivedElapsedRealtime,
            notBeforeElapsedRealtime = fresh.receivedElapsedRealtime - 3_000L,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        ))

        every { raw.locationType } returns AMapLocation.LOCATION_TYPE_FIX_CACHE
        val cached = raw.toSample("reporting")
        assertEquals(now - 1_000L, cached.locationTime)
        assertEquals(LocationQuality.Rejection.SOURCE, ServiceOrderLocationQuality.rejection(
            cached, cached.receivedAt, cached.receivedElapsedRealtime,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        ))
    }

    @Test fun `reused result without a positive source UTC falls back to SDK time`() {
        val sdkTime = System.currentTimeMillis() - 1_000L
        listOf(AMapLocation.LOCATION_TYPE_SAME_REQ, AMapLocation.LOCATION_TYPE_FIX_CACHE).forEach { type ->
            val raw = sample().also {
                every { it.locationType } returns type
                every { it.time } returns sdkTime
            }
            listOf(0L, -1L).forEach { sourceUtc ->
                every { raw.locationUtcTime } returns sourceUtc
                assertEquals(sdkTime, raw.toSample("fallback").locationTime)
            }
        }
    }

    @Test fun `GPS preserves SDK corrected time instead of its uncorrected source UTC`() {
        val now = System.currentTimeMillis()
        val correctedTime = now - 1_000L
        val raw = sample().also {
            every { it.locationType } returns AMapLocation.LOCATION_TYPE_GPS
            every { it.time } returns correctedTime
            every { it.locationUtcTime } returns now - 86_400_000L
        }
        val corrected = raw.toSample("action")
        assertEquals(correctedTime, corrected.locationTime)
        assertNull(ServiceOrderLocationQuality.rejection(
            corrected, corrected.receivedAt, corrected.receivedElapsedRealtime,
            notBeforeElapsedRealtime = corrected.receivedElapsedRealtime - 3_000L,
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        ))
    }

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

    @Test fun `service action does not rebuild a client that still returns network failures`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(90_000)
        listeners.single().onLocationChanged(sample().also { every { it.errorCode } returns 4 })

        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()

        assertEquals(1, listeners.size)
        listeners.single().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        verify(exactly = 1) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `diagnostic and visibility only checks do not replace a silent SDK client`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val clientId = manager.diagnosticSnapshot()["clientId"]
        advanceTimeBy(89_999L)
        assertFalse(manager.isSamplingStalled("order"))
        advanceTimeBy(1L)
        assertTrue(manager.isSamplingStalled("order"))
        assertFalse(manager.isSamplingStalled("another-order"))
        repeat(5) {
            advanceTimeBy(60_000L)
            listeners.single().onLocationChanged(null)
            manager.syncAppVisibility() // No health-timer recovery requested by this read-only check.
            assertTrue(manager.isSamplingStalled("order"))
        }
        assertFalse(collector.isCompleted)
        assertNull(manager.latestEvent())
        assertEquals(clientId, manager.diagnosticSnapshot()["clientId"])
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        verify(exactly = 0) { client.onDestroy() }
        collector.cancelAndJoin()
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `same client fresh point after a long gap resumes reporting and completes the action`() = runTest(dispatcher) {
        val received = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect { received += it } }
        runCurrent()
        val callback = listeners.single()
        val clientId = manager.diagnosticSnapshot()["clientId"]
        callback.onLocationChanged(sample())
        runCurrent()
        advanceTimeBy(300_000L)
        assertTrue(manager.isSamplingStalled("order"))

        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertFalse("The pre-gap point must not satisfy a new action", action.isCompleted)
        callback.onLocationChanged(sample().also {
            every { it.locationTickTime } returns TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime())
        })
        runCurrent()

        assertTrue(action.await() is LocationAcquisition.Success)
        assertEquals(2, received.size)
        assertEquals(clientId, (received.last() as LocationAcquisition.Success).location.requestId)
        assertEquals(clientId, actionDiagnostics.single()["clientId"])
        assertFalse(manager.isSamplingStalled("order"))
        assertEquals(listOf(30_000L, 3_000L, 30_000L), options.map { it.interval })
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.onDestroy() }
        collector.cancelAndJoin()
    }

    @Test fun `action timeout leaves the reporting client available for later callbacks and actions`() = runTest(dispatcher) {
        val received = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect { received += it } }
        runCurrent()
        val callback = listeners.single()
        val clientId = manager.diagnosticSnapshot()["clientId"]
        advanceTimeBy(90_000L)
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        advanceTimeBy(30_000L)
        runCurrent()

        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), action.await())
        assertEquals(30_000L, actionDiagnostics.single()["durationMs"])
        assertEquals(listOf(30_000L, 3_000L, 30_000L), options.map { it.interval })
        assertFalse(collector.isCompleted)
        assertEquals(clientId, manager.diagnosticSnapshot()["clientId"])
        callback.onLocationChanged(sample().also {
            every { it.locationTickTime } returns TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime())
        })
        runCurrent()
        assertEquals(1, received.size)
        assertTrue(manager.acquireServiceOrderLocation() is LocationAcquisition.Success)
        assertEquals(listOf(30_000L, 3_000L, 30_000L), options.map { it.interval })
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        verify(exactly = 0) { client.onDestroy() }
        collector.cancelAndJoin()
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `action timeout without a callback does not invent callback age`() = runTest(dispatcher) {
        val action = async { manager.acquireServiceOrderLocation() }
        advanceUntilIdle()
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), action.await())
        assertEquals(0, actionDiagnostics.single()["callbackCount"])
        assertNull(actionDiagnostics.single()["rawGapMs"])
    }

    @Test fun `cancelled action is one local outcome without changing cleanup`() = runTest(dispatcher) {
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        advanceTimeBy(500)
        action.cancelAndJoin()
        assertEquals("cancelled", actionDiagnostics.single()["result"])
        assertEquals(false, actionDiagnostics.single()["remote"])
        assertEquals(500L, actionDiagnostics.single()["durationMs"])
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
        val diagnostic = actionDiagnostics.single()
        assertEquals("PRECISION", diagnostic["result"])
        assertEquals(true, diagnostic["timedOut"])
        assertEquals(30_000L, diagnostic["durationMs"])
        assertEquals("ACCURACY", diagnostic["qualityReason"])
        assertEquals(1, diagnostic["rejectedCount"])
        assertNotEquals(diagnostic["actionId"], diagnostic["clientId"])
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
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").filterIsInstance<LocationAcquisition.Success>().collect { samples += it.location } }
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
        val second = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").filterIsInstance<LocationAcquisition.Success>().collect { received += it.location } }
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
        val collector = backgroundScope.launch { manager.startContinuousLocation(owner = "test-owner").filterIsInstance<LocationAcquisition.Success>().collect { received += it.location } }
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
        val isolatedManager = ContinuousAmapLocationManager(factory, keyProvider, runtimeReadiness)
        val collector = backgroundScope.launch { isolatedManager.startContinuousLocation(owner = "test-owner").collect() }
        runCurrent()
        collector.cancel()
        keyResponse.complete("test-key")
        collector.join()
        verify(exactly = 0) { factory.create(any()) }
    }

    @Test fun `pause during key lookup prevents a late client start and can resume the same collector`() = runTest(dispatcher) {
        val keyResponse = CompletableDeferred<String>()
        val keyProvider = mockk<AmapApiKeyProvider>()
        coEvery { keyProvider.getAmapApiKey() } coAnswers { keyResponse.await() }
        val isolatedManager = ContinuousAmapLocationManager(factory, keyProvider, runtimeReadiness)
        val events = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch {
            isolatedManager.startContinuousLocation("order-1").collect { events += it }
        }
        runCurrent()
        isolatedManager.setSamplingEnabled("order-1", false)
        keyResponse.complete("test-key")
        runCurrent()
        verify(exactly = 0) { factory.create(any()) }
        assertEquals(listOf(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED)), events)
        assertFalse(collector.isCompleted)
        isolatedManager.setSamplingEnabled("order-1", true)
        verify(exactly = 1) { factory.create(any()) }
        listeners.single().onLocationChanged(sample())
        runCurrent()
        assertTrue(events.last() is LocationAcquisition.Success)
        collector.cancelAndJoin()
    }

    @Test fun `pause and resume retain the owner and collector but discard old client callbacks and warm replay`() = runTest(dispatcher) {
        val events = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch { manager.startContinuousLocation("order-1").collect { events += it } }
        runCurrent()
        val oldCallback = listeners.single()
        oldCallback.onLocationChanged(sample())
        runCurrent()
        val oldRequestId = (events.single() as LocationAcquisition.Success).location.requestId

        manager.setSamplingEnabled("order-1", false)
        runCurrent()
        assertFalse(collector.isCompleted)
        assertEquals(LocationFailure.SERVICE_DISABLED, (events.last() as LocationAcquisition.Failure).reason)
        assertEquals(LocationFailure.SERVICE_DISABLED,
            (manager.acquireServiceOrderLocation() as LocationAcquisition.Failure).reason)
        assertEquals(0, currentTime)
        verify(exactly = 1) { client.onDestroy() }
        val pausedCount = events.size
        oldCallback.onLocationChanged(sample())
        oldCallback.onLocationChanged(sample().also { every { it.errorCode } returns 4 })
        runCurrent()
        assertEquals(pausedCount, events.size)
        assertFalse(manager.isSamplingStalled("order-1"))
        manager.setSamplingEnabled("other-order", true)
        verify(exactly = 1) { factory.create(any()) }

        manager.setSamplingEnabled("order-1", true)
        runCurrent()
        assertNull(manager.latestEvent())
        oldCallback.onLocationChanged(sample())
        runCurrent()
        assertNull(manager.latestEvent())
        assertEquals(pausedCount, events.size)
        verify(exactly = 2) { factory.create(any()) }
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertFalse("Pre-pause replay must not satisfy an action after resume", action.isCompleted)
        listeners.last().onLocationChanged(sample())
        assertTrue(action.await() is LocationAcquisition.Success)
        runCurrent()
        val resumed = (events.last() as LocationAcquisition.Success).location
        assertNotEquals(oldRequestId, resumed.requestId)
        assertFalse(collector.isCompleted)
        collector.cancelAndJoin()
        verify(exactly = 2) { client.onDestroy() }
    }

    @Test fun `initially disabled session never starts SDK and resumes without a new collection`() = runTest(dispatcher) {
        val events = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch {
            manager.startContinuousLocation("order-1", initiallyEnabled = false).collect { events += it }
        }
        runCurrent()
        assertEquals(listOf(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED)), events)
        verify(exactly = 0) { factory.create(any()) }
        assertFalse(collector.isCompleted)
        assertEquals(LocationFailure.SERVICE_DISABLED,
            (manager.acquireServiceOrderLocation() as LocationAcquisition.Failure).reason)
        manager.setSamplingEnabled("order-1", true)
        verify(exactly = 1) { factory.create(any()) }
        listeners.single().onLocationChanged(sample())
        runCurrent()
        assertTrue(events.last() is LocationAcquisition.Success)
        collector.cancelAndJoin()
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `pause completes an in flight action immediately without waiting for its timeout`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order-1").collect() }
        runCurrent()
        val action = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertFalse(action.isCompleted)
        manager.setSamplingEnabled("order-1", false)
        assertEquals(LocationAcquisition.Failure(LocationFailure.SERVICE_DISABLED), action.await())
        assertEquals(0, currentTime)
        assertFalse(collector.isCompleted)
        verify(exactly = 1) { client.onDestroy() }
        collector.cancelAndJoin()
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
        assertEquals(30_000, options.last().httpTimeOut)
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

    @Test fun `closing diagnostics retain the client purpose after reporting owner is released`() = runTest(dispatcher) {
        val events = mutableListOf<Map<String, Any?>>()
        every { runtimeReadiness.diagnosticSnapshot() } returns mapOf(
            "backgroundRestricted" to true,
            "restrictBackgroundStatus" to ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED,
            "lowPowerStandbyEnabled" to true,
        )
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "123"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } answers {
            events += arg<Map<String, Any?>>(4)
        }
        every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } answers {
            events += arg<Map<String, Any?>>(3)
        }
        val collector = backgroundScope.launch { manager.startContinuousLocation("order-1").collect() }
        runCurrent()
        repeat(2) {
            listeners.single().onLocationChanged(sample().also { every { it.errorCode } returns 4 })
            runCurrent()
        }
        collector.cancelAndJoin()
        assertEquals("order_reporting", events.single { it["stage"] == "first" }["clientPurpose"])
        assertEquals("order_reporting", events.single { it["stage"] == "finished" }["clientPurpose"])
        events.forEach { fields ->
            assertEquals("bg=1,ds=3,lps=1", fields["backgroundPolicy"])
            assertFalse(fields.containsKey("backgroundRestricted"))
            assertFalse(fields.containsKey("restrictBackgroundStatus"))
            assertFalse(fields.containsKey("lowPowerStandbyEnabled"))
        }
    }

    @Test fun `repeated SDK network failures keep the same client and resume successful sampling`() = runTest(dispatcher) {
        val received = mutableListOf<com.ytone.longcare.model.LocationResult>()
        val collector = backgroundScope.launch {
            manager.startContinuousLocation(owner = "order-1").filterIsInstance<LocationAcquisition.Success>().collect { received += it.location }
        }
        runCurrent()
        val callback = listeners.single()
        val failure = sample().also { every { it.errorCode } returns AMapLocation.ERROR_CODE_FAILURE_CONNECTION }
        repeat(5) {
            callback.onLocationChanged(failure)
            runCurrent()
            assertEquals(LocationFailure.NETWORK, (manager.latestEvent() as LocationAcquisition.Failure).reason)
            advanceTimeBy(30_000)
            assertFalse(manager.isSamplingStalled("order-1"))
        }
        assertTrue("SDK failures must not enter the coordinate upload stream", received.isEmpty())
        callback.onLocationChanged(sample())
        runCurrent()
        assertEquals(1, received.size)
        assertEquals(1, listeners.size)
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `temporary Main immediate action records terminal error before releasing its client`() {
        assertTemporaryActionDiagnostic(
            callbackCodes = listOf(AMapLocation.ERROR_CODE_FAILURE_LOCATION_PERMISSION),
            expectedEvent = "amap_continuous_location_error",
        )
    }

    @Test fun `temporary Main immediate action settles SDK recovery in its closing summary`() {
        assertTemporaryActionDiagnostic(
            callbackCodes = listOf(AMapLocation.ERROR_CODE_FAILURE_CONNECTION, AMapLocation.LOCATION_SUCCESS),
            expectedEvent = "amap_continuous_location_recovered",
        )
    }

    @Test fun `default SDK network timeout never extends the service action budget`() = runTest(dispatcher) {
        val request = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertEquals(30_000, options.single().httpTimeOut)
        advanceTimeBy(29_999)
        runCurrent()
        assertFalse(request.isCompleted)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), request.await())
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 1) { client.stopLocation() }
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `silent startup has one visibility compensation then waits without SDK polling`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        repeat(8) { manager.syncAppVisibility() }
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }
        repeat(8) { manager.syncAppVisibility(fromHealthTimer = true) }
        verify(exactly = 2) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        listeners.single().onLocationChanged(sample())
        repeat(8) { manager.syncAppVisibility() }
        listeners.single().onLocationChanged(sample())
        verify(exactly = 3) { client.notifyAppEnterForeBack(true) }
        collector.cancelAndJoin()
    }

    @Test fun `successful initialization syncs latest visibility and later only actual changes`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        every { runtimeReadiness.isAppVisible() } returns false
        listeners.single().onLocationChanged(sample())
        repeat(5) { manager.syncAppVisibility() }
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 1) { client.notifyAppEnterForeBack(false) }
        every { runtimeReadiness.isAppVisible() } returns true
        repeat(5) { manager.syncAppVisibility() }
        verify(exactly = 2) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 1) { client.startLocation() }
        collector.cancelAndJoin()
        every { runtimeReadiness.isAppVisible() } returns false
        manager.syncAppVisibility()
        listeners.single().onLocationChanged(sample())
        verify(exactly = 1) { client.notifyAppEnterForeBack(false) }
    }

    @Test fun `unknown health visibility preserves startup compensation until an actual attempt`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }

        every { runtimeReadiness.isAppVisible() } returns null
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }

        every { runtimeReadiness.isAppVisible() } returns true
        repeat(8) { manager.syncAppVisibility(fromHealthTimer = true) }
        verify(exactly = 2) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
    }

    @Test fun `unknown first success visibility is compensated once by a later health check`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }

        every { runtimeReadiness.isAppVisible() } returns null
        listeners.single().onLocationChanged(sample())
        assertTrue(manager.latestEvent() is LocationAcquisition.Success)
        verify(exactly = 1) { client.notifyAppEnterForeBack(true) }

        every { runtimeReadiness.isAppVisible() } returns true
        repeat(8) { manager.syncAppVisibility(fromHealthTimer = true) }
        verify(exactly = 2) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 1) { client.startLocation() }
        verify(exactly = 0) { client.stopLocation() }
        collector.cancelAndJoin()
    }

    @Test fun `unknown visibility is skipped and SDK sync failure does not stop location`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns null
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        manager.syncAppVisibility()
        verify(exactly = 0) { client.notifyAppEnterForeBack(any()) }
        every { runtimeReadiness.isAppVisible() } returns false
        every { client.notifyAppEnterForeBack(false) } throws IllegalStateException("SDK not ready")
        listeners.single().onLocationChanged(sample())
        assertTrue(manager.latestEvent() is LocationAcquisition.Success)
        verify(exactly = 0) { client.stopLocation() }
        every { client.notifyAppEnterForeBack(false) } just Runs
        repeat(8) { manager.syncAppVisibility(fromHealthTimer = true) }
        verify(exactly = 1) { client.notifyAppEnterForeBack(false) }
        every { runtimeReadiness.isAppVisible() } returns true
        manager.syncAppVisibility()
        every { runtimeReadiness.isAppVisible() } returns false
        manager.syncAppVisibility()
        verify(exactly = 2) { client.notifyAppEnterForeBack(false) }
        collector.cancelAndJoin()
    }

    @Test fun `new client has independent visibility startup state`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        listeners.last().onLocationChanged(sample())
        manager.setSamplingEnabled("order", false)
        every { runtimeReadiness.isAppVisible() } returns false
        manager.setSamplingEnabled("order", true)
        manager.syncAppVisibility(fromHealthTimer = true)
        listeners.last().onLocationChanged(sample())
        manager.syncAppVisibility()
        verify(exactly = 2) { client.notifyAppEnterForeBack(true) }
        verify(exactly = 3) { client.notifyAppEnterForeBack(false) }
        verify(exactly = 2) { client.startLocation() }
        collector.cancelAndJoin()
    }

    @Test fun `background silence replaces clients with a bounded phase budget and ignores old callbacks`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val received = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect { received += it } }
        runCurrent()
        val oldCallback = listeners.single()
        val oldClientId = manager.diagnosticSnapshot()["clientId"]
        advanceTimeBy(30_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 1) { factory.create(any()) }
        advanceTimeBy(15_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 2) { factory.create(any()) }
        verify(exactly = 1) { client.onDestroy() }
        assertNotEquals(oldClientId, manager.diagnosticSnapshot()["clientId"])
        oldCallback.onLocationChanged(freshSample())
        runCurrent()
        assertTrue(received.isEmpty())
        advanceTimeBy(30_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 2) { factory.create(any()) }
        advanceTimeBy(15_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 3) { factory.create(any()) }
        repeat(8) {
            advanceTimeBy(45_000L)
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 3) { factory.create(any()) }
        assertTrue(manager.isSamplingStalled("order"))
        listeners.last().onLocationChanged(freshSample())
        runCurrent()
        assertEquals(1, received.size)
        // A single good point must not re-arm an endless recreate/first-point/stall loop.
        advanceTimeBy(90_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 3) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `foreground silence can recover without going to background`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(45_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 1) { factory.create(any()) }
        advanceTimeBy(45_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 2) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `stale repeated source callbacks cannot hide ineffective sampling`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val source = SystemClock.elapsedRealtime()
        listeners.last().onLocationChanged(freshSample(source))
        repeat(3) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source))
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 2) { factory.create(any()) }
        // The new SDK client's same old source must not reset the phase watermark or budget.
        repeat(4) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source))
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 3) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `new source ticks at unchanged coordinates are healthy samples`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        repeat(10) {
            advanceTimeBy(30_000L)
            listeners.single().onLocationChanged(freshSample())
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 1) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `network errors and poor accuracy alone do not cause SDK restart loops`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        repeat(10) {
            advanceTimeBy(30_000L)
            listeners.single().onLocationChanged(freshSample().also { raw -> every { raw.errorCode } returns 4 })
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        repeat(10) {
            advanceTimeBy(30_000L)
            listeners.single().onLocationChanged(freshSample().also { raw -> every { raw.accuracy } returns 900f })
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 1) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `replacement option failure after stopping old client is cleaned up and retried`() =
        assertRecoveryFailureRetry(failDuringOption = true)

    @Test fun `replacement start failure after stopping old client is cleaned up and retried`() =
        assertRecoveryFailureRetry(failDuringOption = false)

    private fun assertRecoveryFailureRetry(failDuringOption: Boolean) = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val broken = mockk<AMapLocationClient>(relaxed = true)
        val replacement = mockk<AMapLocationClient>(relaxed = true)
        val replacementListener = slot<AMapLocationListener>()
        every { replacement.setLocationListener(capture(replacementListener)) } just Runs
        if (failDuringOption) every { broken.setLocationOption(any()) } throws IllegalStateException("option")
        else every { broken.startLocation() } throws IllegalStateException("start")
        every { factory.create(any()) } returnsMany listOf(client, broken, replacement).map(::SdkAmapClientHandle)
        val received = mutableListOf<LocationAcquisition>()
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect { received += it } }
        runCurrent()
        val oldListener = listeners.single()
        advanceTimeBy(45_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 1) { client.onDestroy() }
        verify(exactly = 1) { broken.onDestroy() }
        verify(exactly = 1) { replacement.startLocation() }
        oldListener.onLocationChanged(freshSample())
        runCurrent()
        assertTrue(received.isEmpty())
        replacementListener.captured.onLocationChanged(freshSample())
        runCurrent()
        assertEquals(1, received.size)
        assertNotNull(manager.diagnosticSnapshot()["clientId"])
        collector.cancelAndJoin()
        verify(exactly = 1) { replacement.onDestroy() }
    }

    @Test fun `exhausted replacement failures propagate without a logically active stopped client`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val broken = mockk<AMapLocationClient>(relaxed = true)
        every { broken.startLocation() } throws IllegalStateException("start")
        every { factory.create(any()) } returnsMany listOf(client, broken, broken).map(::SdkAmapClientHandle)
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val oldListener = listeners.single()
        advanceTimeBy(45_000L)
        val failure = runCatching { manager.syncAppVisibility(fromHealthTimer = true) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertNull(manager.diagnosticSnapshot()["clientId"])
        oldListener.onLocationChanged(freshSample())
        assertNull(manager.latestEvent())
        verify(exactly = 3) { factory.create(any()) }
        verify(exactly = 2) { broken.onDestroy() }
        collector.cancelAndJoin()
    }

    @Test fun `pause resume generation invalidates an in flight recovery key read`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val keyResponse = CompletableDeferred<String>()
        val keys = mockk<AmapApiKeyProvider>()
        var keyReads = 0
        coEvery { keys.getAmapApiKey() } coAnswers { if (++keyReads == 2) keyResponse.await() else "key" }
        val isolated = ContinuousAmapLocationManager(factory, keys, runtimeReadiness)
        val collector = backgroundScope.launch { isolated.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(45_000L)
        val recovery = launch { isolated.syncAppVisibility(fromHealthTimer = true) }
        runCurrent()
        isolated.setSamplingEnabled("order", false)
        isolated.setSamplingEnabled("order", true)
        val resumedClientId = isolated.diagnosticSnapshot()["clientId"]
        keyResponse.complete("late-key")
        recovery.join()
        assertEquals(resumedClientId, isolated.diagnosticSnapshot()["clientId"])
        verify(exactly = 2) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `stop and cancellation during recovery key read cannot resurrect sampling`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val keyResponse = CompletableDeferred<String>()
        val keys = mockk<AmapApiKeyProvider>()
        var keyReads = 0
        coEvery { keys.getAmapApiKey() } coAnswers {
            if (++keyReads == 2) withContext(NonCancellable) { keyResponse.await() } else "key"
        }
        val isolated = ContinuousAmapLocationManager(factory, keys, runtimeReadiness)
        val collector = backgroundScope.launch { isolated.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(45_000L)
        val recovery = launch { isolated.syncAppVisibility(fromHealthTimer = true) }
        runCurrent()
        recovery.cancel()
        collector.cancelAndJoin()
        keyResponse.complete("late-key")
        recovery.join()
        assertNull(isolated.diagnosticSnapshot()["clientId"])
        verify(exactly = 1) { factory.create(any()) }
    }

    @Test fun `quality watchdog uses the receipt clock even when clock advances during callback mapping`() = runTest(dispatcher) {
        var clockReadOffset = 0L
        every { SystemClock.elapsedRealtime() } answers {
            initialElapsedRealtime + dispatcher.scheduler.currentTime + clockReadOffset++
        }
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        var source = 0L
        repeat(3) {
            advanceTimeBy(30_000L)
            source = SystemClock.elapsedRealtime()
            listeners.last().onLocationChanged(freshSample(source))
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        repeat(2) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source))
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 1) { factory.create(any()) }
        advanceTimeBy(30_000L)
        listeners.last().onLocationChanged(freshSample(source))
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 2) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `stale sample evidence survives intervening errors until a genuinely advancing fix`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val source = SystemClock.elapsedRealtime()
        listeners.last().onLocationChanged(freshSample(source))
        repeat(8) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source))
            listeners.last().onLocationChanged(freshSample().also { raw -> every { raw.errorCode } returns 4 })
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 3) { factory.create(any()) }
        assertTrue(manager.isSamplingStalled("order"))
        listeners.last().onLocationChanged(freshSample())
        assertFalse(manager.isSamplingStalled("order"))
        collector.cancelAndJoin()
    }

    @Test fun `fresh progress during pending recovery wins over an obsolete missing key`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val keyResponse = CompletableDeferred<String?>()
        val keys = mockk<AmapApiKeyProvider>()
        var keyReads = 0
        coEvery { keys.getAmapApiKey() } coAnswers { if (++keyReads == 2) keyResponse.await() else "key" }
        val isolated = ContinuousAmapLocationManager(factory, keys, runtimeReadiness)
        val collector = backgroundScope.launch { isolated.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(45_000L)
        val recovery = async { isolated.syncAppVisibility(fromHealthTimer = true) }
        runCurrent()
        listeners.single().onLocationChanged(freshSample())
        keyResponse.complete(null)
        recovery.await()
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 0) { client.onDestroy() }
        assertFalse(isolated.isSamplingStalled("order"))
        collector.cancelAndJoin()
    }

    @Test fun `reentrant pause from old client cleanup wins over replacement startup`() = runTest(dispatcher) {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        every { runtimeReadiness.isAppVisible() } returns false
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        every { client.onDestroy() } answers {
            runBlocking { manager.setSamplingEnabled("order", false) }
        }
        advanceTimeBy(45_000L)
        manager.syncAppVisibility(fromHealthTimer = true)
        assertNull(manager.diagnosticSnapshot()["clientId"])
        verify(exactly = 1) { factory.create(any()) }
        collector.cancelAndJoin()
    }

    @Test fun `owner switch invalidates old pending recovery and late callbacks without clearing new owner`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val keyResponse = CompletableDeferred<String>()
        val keys = mockk<AmapApiKeyProvider>()
        var keyReads = 0
        coEvery { keys.getAmapApiKey() } coAnswers { if (++keyReads == 2) keyResponse.await() else "key" }
        val replacement = mockk<AMapLocationClient>(relaxed = true)
        val replacementListener = slot<AMapLocationListener>()
        every { replacement.setLocationListener(capture(replacementListener)) } just Runs
        every { factory.create(any()) } returnsMany listOf(client, replacement).map(::SdkAmapClientHandle)
        val isolated = ContinuousAmapLocationManager(factory, keys, runtimeReadiness)
        val first = backgroundScope.launch { isolated.startContinuousLocation("first").collect() }
        runCurrent()
        val oldListener = listeners.single()
        advanceTimeBy(45_000L)
        val recovery = launch { isolated.syncAppVisibility(fromHealthTimer = true) }
        runCurrent()
        val received = mutableListOf<LocationAcquisition>()
        val second = backgroundScope.launch { isolated.startContinuousLocation("second").collect { received += it } }
        runCurrent()
        val newClientId = isolated.diagnosticSnapshot()["clientId"]
        first.cancelAndJoin()
        keyResponse.complete("obsolete-key")
        recovery.join()
        oldListener.onLocationChanged(freshSample())
        runCurrent()
        assertTrue(received.isEmpty())
        assertEquals(newClientId, isolated.diagnosticSnapshot()["clientId"])
        verify(exactly = 2) { factory.create(any()) }
        verify(exactly = 0) { replacement.onDestroy() }
        replacementListener.captured.onLocationChanged(freshSample())
        runCurrent()
        assertEquals(1, received.size)
        second.cancelAndJoin()
    }

    @Test fun `fresh low accuracy sources clear stale evidence without qualifying for upload`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val initialSource = SystemClock.elapsedRealtime()
        listeners.single().onLocationChanged(freshSample(initialSource))
        advanceTimeBy(30_000L)
        listeners.single().onLocationChanged(freshSample(initialSource))
        val evaluator = LocationSampleEvaluator(initialSource, LocationClock())
        repeat(4) {
            advanceTimeBy(30_000L)
            val lowAccuracy = freshSample().also { raw -> every { raw.accuracy } returns 500f }
            listeners.last().onLocationChanged(lowAccuracy)
            assertEquals(LocationQuality.Rejection.ACCURACY, evaluator.rejection(lowAccuracy.toSample("candidate")))
            manager.syncAppVisibility(fromHealthTimer = true)
            assertFalse(manager.isSamplingStalled("order"))
        }
        verify(exactly = 1) { factory.create(any()) }
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=0/150,r=n:0/2"))
        collector.cancelAndJoin()
    }

    @Test fun `fresh eligible source clears stale evidence and advances both observation gaps`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val source = SystemClock.elapsedRealtime()
        listeners.single().onLocationChanged(freshSample(source))
        advanceTimeBy(30_000L)
        listeners.single().onLocationChanged(freshSample(source))
        advanceTimeBy(60_000L)
        listeners.single().onLocationChanged(freshSample())
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 1) { factory.create(any()) }
        assertFalse(manager.isSamplingStalled("order"))
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=0/0,r=n:0/2"))
        collector.cancelAndJoin()
    }

    @Test fun `newer numeric source cannot clear stale evidence when time or metadata is invalid`() = runTest(dispatcher) {
        val invalidSources: List<(AMapLocation) -> Unit> = listOf(
            { raw -> every { raw.locationTickTime } returns TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime() + 1_000L) },
            { raw -> every { raw.accuracy } returns Float.NaN },
            { raw -> every { raw.accuracy } returns 0f },
            { raw -> every { raw.isMock } returns true },
            { raw -> every { raw.isFixLastLocation } returns true },
            { raw -> every { raw.trustedLevel } returns AMapLocation.TRUSTED_LEVEL_BAD },
            { raw -> every { raw.coordType } returns "WGS84" },
            { raw -> every { raw.latitude } returns Double.NaN },
            { raw -> every { raw.locationType } returns 0 },
        )
        invalidSources.forEachIndexed { index, invalidate ->
            val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
            runCurrent()
            val source = SystemClock.elapsedRealtime()
            listeners.last().onLocationChanged(freshSample(source))
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source))
            repeat(2) {
                advanceTimeBy(30_000L)
                listeners.last().onLocationChanged(freshSample().also(invalidate))
                manager.syncAppVisibility(fromHealthTimer = true)
            }
            verify(exactly = (index + 1) * 2) { factory.create(any()) }
            assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=90/90,r=s:1/2"))
            collector.cancelAndJoin()
        }
    }

    @Test fun `pre phase source cannot establish progress and hide the initial stalled period`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val phaseStart = SystemClock.elapsedRealtime()
        advanceTimeBy(30_000L)
        // This is younger than the age limit but predates this enabled phase.
        listeners.single().onLocationChanged(freshSample(phaseStart - 1L))
        repeat(2) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample().also { every { it.errorCode } returns 4 })
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 2) { factory.create(any()) }
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=?/?,r=s:1/2"))
        collector.cancelAndJoin()
    }

    @Test fun `repeated low accuracy source eventually stalls but advancing low accuracy source recovers progress`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        val source = SystemClock.elapsedRealtime()
        listeners.single().onLocationChanged(freshSample(source).also { every { it.accuracy } returns 500f })
        repeat(3) {
            advanceTimeBy(30_000L)
            listeners.last().onLocationChanged(freshSample(source).also { every { it.accuracy } returns 500f })
            manager.syncAppVisibility(fromHealthTimer = true)
        }
        verify(exactly = 2) { factory.create(any()) }
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=90/?,r=s:1/2"))
        listeners.last().onLocationChanged(freshSample().also { every { it.accuracy } returns 500f })
        assertFalse(manager.isSamplingStalled("order"))
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=0/?,r=s:1/2"))
        collector.cancelAndJoin()
    }

    @Test fun `low accuracy source progress during pending stale recovery makes missing key obsolete`() = runTest(dispatcher) {
        val keyResponse = CompletableDeferred<String?>()
        val keys = mockk<AmapApiKeyProvider>()
        var keyReads = 0
        coEvery { keys.getAmapApiKey() } coAnswers { if (++keyReads == 2) keyResponse.await() else "key" }
        val isolated = ContinuousAmapLocationManager(factory, keys, runtimeReadiness)
        val collector = backgroundScope.launch { isolated.startContinuousLocation("order").collect() }
        runCurrent()
        val source = SystemClock.elapsedRealtime()
        listeners.single().onLocationChanged(freshSample(source))
        advanceTimeBy(90_000L)
        listeners.single().onLocationChanged(freshSample(source))
        val recovery = async { isolated.syncAppVisibility(fromHealthTimer = true) }
        runCurrent()
        assertEquals(2, keyReads)
        listeners.single().onLocationChanged(freshSample().also { every { it.accuracy } returns 500f })
        keyResponse.complete(null)
        recovery.await()
        verify(exactly = 1) { factory.create(any()) }
        verify(exactly = 0) { client.onDestroy() }
        assertFalse(isolated.isSamplingStalled("order"))
        assertTrue(isolated.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=0/90,r=n:0/2"))
        collector.cancelAndJoin()
    }

    @Test fun `recovery records the accepted trigger without a third visibility decision`() = runTest(dispatcher) {
        every { runtimeReadiness.isAppVisible() } returns false
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        advanceTimeBy(45_000L)
        // Visibility synchronization, initial recovery check, and post-key check see background.
        // The next read sees foreground: it must not rewrite the reason for the accepted attempt.
        var reads = 0
        every { runtimeReadiness.isAppVisible() } answers { ++reads > 3 }
        manager.syncAppVisibility(fromHealthTimer = true)
        verify(exactly = 2) { factory.create(any()) }
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=?/?,r=c:1/2"))
        collector.cancelAndJoin()
    }

    @Test fun `progress snapshot is bounded unknown until observed and resets with enabled phase`() = runTest(dispatcher) {
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        assertEquals(setOf("clientId", "runtime", "sdkState"), manager.diagnosticSnapshot().keys)
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=?/?,r=n:0/2"))
        listeners.single().onLocationChanged(freshSample())
        advanceTimeBy(1_000_000_000L)
        val state = manager.diagnosticSnapshot()["sdkState"].toString()
        assertTrue(state.endsWith("gS=999999+/999999+,r=n:0/2"))
        assertTrue(state.length <= 160)
        manager.setSamplingEnabled("order", false)
        manager.setSamplingEnabled("order", true)
        assertTrue(manager.diagnosticSnapshot()["sdkState"].toString().endsWith("gS=?/?,r=n:0/2"))
        collector.cancelAndJoin()
    }

    @Test fun `real action payload retains manager recovery state and time evidence`() = runTest(dispatcher) {
        unmockkObject(LocationEventTracker)
        mockkObject(CrashReportGateway)
        every { CrashReportGateway.userId } returns "2147483647"
        val delivered = mutableListOf<DiagnosticException>()
        every { CrashReportGateway.postCaughtException(any()) } answers { delivered += firstArg<DiagnosticException>() }
        every { CrashReportGateway.recordBreadcrumb(any(), any()) } just Runs
        val collector = backgroundScope.launch { manager.startContinuousLocation("order").collect() }
        runCurrent()
        listeners.single().onLocationChanged(freshSample())
        val acquisition = async { manager.acquireServiceOrderLocation() }
        runCurrent()
        assertTrue(acquisition.await() is LocationAcquisition.Success)
        val report = delivered.last { it.fields["eventCode"] == "service_action_result" }
        val state = requireNotNull(report.fields["sdkState"])
        assertTrue(state.endsWith("gS=0/0,r=n:0/2"))
        listOf("actionId", "clientId", "time", "runtime", "sdkState").forEach {
            assertNotNull("Missing $it: ${report.message}", report.fields[it])
        }
        assertTrue(state.length <= 160)
        assertTrue(requireNotNull(report.message).length <= 950)
        assertTrue(report.fields.size <= 30)
        assertFalse(report.fields.containsKey("latitude"))
        assertFalse(report.fields.containsKey("longitude"))
        collector.cancelAndJoin()
    }

    private fun freshSample(source: Long = SystemClock.elapsedRealtime()): AMapLocation = sample().also {
        every { it.time } returns System.currentTimeMillis()
        every { it.locationTickTime } returns TimeUnit.MILLISECONDS.toNanos(source)
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
        every { locationTickTime } returns 0L
        every { isMock } returns false
        every { isFixLastLocation } returns false
    }

    /** Plain executor callbacks model Handler delivery outside an active coroutine event loop. */
    private fun assertTemporaryActionDiagnostic(callbackCodes: List<Int>, expectedEvent: String) = runBlocking {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "amap-test-main").apply { isDaemon = true } }
        val mainThread = executor.submit<Thread> { Thread.currentThread() }.get(5, TimeUnit.SECONDS)
        val immediateMain = object : MainCoroutineDispatcher() {
            override val immediate: MainCoroutineDispatcher get() = this
            override fun isDispatchNeeded(context: CoroutineContext): Boolean = Thread.currentThread() !== mainThread
            override fun dispatch(context: CoroutineContext, block: Runnable) { executor.execute(block) }
        }
        val started = CountDownLatch(1)
        val events = mutableListOf<Pair<String, Map<String, Any?>>>()
        mockkObject(DiagnosticEventTracker)
        every { DiagnosticEventTracker.currentUserId() } returns "123"
        every { DiagnosticEventTracker.trackError(any(), any(), any(), any(), any(), any()) } answers { events += arg<String>(1) to arg<Map<String, Any?>>(4) }
        every { DiagnosticEventTracker.trackEvent(any(), any(), any(), any(), any(), any()) } answers { events += arg<String>(1) to arg<Map<String, Any?>>(3) }
        every { client.startLocation() } answers { started.countDown() }
        Dispatchers.setMain(immediateMain)
        val action = async(Dispatchers.Main.immediate) { manager.acquireServiceOrderLocation() }
        try {
            assertTrue("Temporary action did not start", started.await(5, TimeUnit.SECONDS))
            callbackCodes.forEach { code ->
                val raw = sample().also {
                    every { it.errorCode } returns code
                    every { it.time } answers { System.currentTimeMillis() - 500L }
                }
                executor.submit { listeners.single().onLocationChanged(raw) }.get(5, TimeUnit.SECONDS)
            }
            val result = action.await()
            if (callbackCodes.last() == AMapLocation.LOCATION_SUCCESS) assertTrue(result is LocationAcquisition.Success)
            else assertEquals(LocationFailure.PERMISSION, (result as LocationAcquisition.Failure).reason)
            verify(exactly = 1) { client.onDestroy() }
            assertTrue("Callback diagnostics were lost during synchronous action cleanup: $expectedEvent",
                events.any { it.first == expectedEvent })
            if (callbackCodes.last() == AMapLocation.LOCATION_SUCCESS) {
                val recovery = events.single { it.first == expectedEvent }.second
                assertEquals("finished", recovery["stage"])
                assertEquals(1L, recovery["failureCount"])
                assertEquals(1L, recovery["recoveryCount"])
            }
        } finally {
            action.cancelAndJoin()
            Dispatchers.setMain(dispatcher)
            executor.shutdownNow()
        }
    }
}
