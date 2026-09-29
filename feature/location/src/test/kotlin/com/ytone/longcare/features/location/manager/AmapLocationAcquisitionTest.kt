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
        factory = mockk<AmapLocationClientFactory> { every { create(any()) } returns client }
        manager = ContinuousAmapLocationManager(factory, key)
    }

    @After fun cleanup() { Dispatchers.resetMain(); unmockkAll() }

    @Test fun `single config metadata duplicate callback and cleanup are real SDK boundary behavior`() = runTest(dispatcher) {
        val result = async { manager.acquireCurrentLocation() }
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
        val job = async { manager.acquireCurrentLocation() }
        runCurrent()
        job.cancelAndJoin()
        listeners.single().onLocationChanged(sample())
        runCurrent()
        assertEquals(1, options.size)
        verify(exactly = 1) { client.stopLocation() }
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `timeout destroys isolated client`() = runTest(dispatcher) {
        val job = async { manager.acquireCurrentLocation() }
        advanceUntilIdle()
        assertEquals(LocationAcquisition.Failure(LocationFailure.TIMEOUT), job.await())
        verify(exactly = 1) { client.onDestroy() }
    }

    @Test fun `single completion and cancellation never stop continuous client`() = runTest(dispatcher) {
        val continuous = mockk<AMapLocationClient>(relaxed = true)
        val continuousListener = slot<AMapLocationListener>()
        val continuousOption = slot<AMapLocationClientOption>()
        every { continuous.setLocationListener(capture(continuousListener)) } just Runs
        every { continuous.setLocationOption(capture(continuousOption)) } just Runs
        every { factory.create(any()) } returnsMany listOf(continuous, client, client)
        val samples = mutableListOf<com.ytone.longcare.model.LocationResult>()
        val collector = backgroundScope.launch { manager.startContinuousLocation().collect { samples += it } }
        runCurrent()
        assertFalse(continuousOption.captured.isLocationCacheEnable)
        assertFalse(continuousOption.captured.isOnceLocation)
        assertEquals(30_000, continuousOption.captured.interval)
        val single = async { manager.acquireCurrentLocation() }
        runCurrent()
        listeners.single().onLocationChanged(sample())
        assertTrue(single.await() is LocationAcquisition.Success)
        val cancelled = async { manager.acquireCurrentLocation() }
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

    private fun sample(): AMapLocation = mockk {
        every { errorCode } returns 0
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
