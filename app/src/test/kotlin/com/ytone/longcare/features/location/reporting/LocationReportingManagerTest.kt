package com.ytone.longcare.features.location.reporting

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.features.location.manager.LocationSampleStore
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationReportingManagerTest {
    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
        mockkObject(LocationEventTracker)
        every { LocationEventTracker.trackEvent(any(), any()) } just runs
        every { LocationEventTracker.trackLocationSample(any(), any(), any(), any()) } just runs
        every { LocationEventTracker.trackError(any(), any(), any()) } just runs
    }

    @After
    fun tearDown() {
        clearAllMocks()
        unmockkAll()
    }

    @Test
    fun `valid current sample is uploaded directly without a queue`() = runTest {
        val fixture = fixture()
        coEvery { fixture.repository.addPosition(any(), any(), any()) } returns ApiResult.Success(Unit)
        fixture.manager.startReporting(OrderKey(100L, 0))
        runCurrent()

        fixture.flow.emit(sample(latitude = 31.23, longitude = 121.47))
        runCurrent()

        coVerify(exactly = 1) { fixture.repository.addPosition(100L, 31.23, 121.47) }
        fixture.manager.stopReporting()
    }

    @Test
    fun `failed sample is dropped and next sample is uploaded once`() = runTest {
        val fixture = fixture()
        coEvery { fixture.repository.addPosition(any(), any(), any()) } returnsMany listOf(
            ApiResult.Exception(IllegalStateException("offline")),
            ApiResult.Success(Unit),
        )
        fixture.manager.startReporting(OrderKey(200L, 0))
        runCurrent()

        fixture.flow.emit(sample(latitude = 31.0, longitude = 121.0))
        runCurrent()
        fixture.flow.emit(sample(latitude = 31.1, longitude = 121.1))
        runCurrent()

        coVerify(exactly = 1) { fixture.repository.addPosition(200L, 31.0, 121.0) }
        coVerify(exactly = 1) { fixture.repository.addPosition(200L, 31.1, 121.1) }
        coVerify(exactly = 2) { fixture.repository.addPosition(any(), any(), any()) }
        fixture.manager.stopReporting()
    }

    @Test
    fun `sample captured before current order session is never uploaded`() = runTest {
        val fixture = fixture(replay = 1)
        fixture.flow.emit(sample(locationTime = System.currentTimeMillis() - 30_000L))

        fixture.manager.startReporting(OrderKey(300L, 0))
        runCurrent()

        coVerify(exactly = 0) { fixture.repository.addPosition(any(), any(), any()) }
        fixture.manager.stopReporting()
    }

    @Test
    fun `stopping session cancels in flight upload and releases foreground owner`() = runTest {
        val fixture = fixture()
        coEvery { fixture.repository.addPosition(any(), any(), any()) } coAnswers {
            awaitCancellation()
        }
        fixture.manager.startReporting(OrderKey(400L, 0))
        runCurrent()
        fixture.flow.emit(sample())
        runCurrent()

        fixture.manager.stopReporting()
        runCurrent()

        verify(exactly = 1) { fixture.facade.releaseKeepAlive("location_report_400") }
        coVerify(exactly = 1) { fixture.repository.addPosition(any(), any(), any()) }
    }

    @Test
    fun `start and stop acquire and release only the active in memory order`() = runTest {
        val fixture = fixture()
        val order = OrderKey(500L, 0)

        fixture.manager.startReporting(order)
        runCurrent()

        fixture.manager.stopReporting()
        runCurrent()
        verify(exactly = 1) { fixture.facade.acquireKeepAlive("location_report_500") }
        verify(exactly = 1) { fixture.facade.releaseKeepAlive("location_report_500") }
    }

    @Test
    fun `old stop cannot release the owner of a restarted same order`() {
        val owners = ConcurrentHashMap.newKeySet<String>()
        val releaseEntered = CountDownLatch(1)
        val allowRelease = CountDownLatch(1)
        val blockFirstRelease = AtomicBoolean(true)
        val facade = mockk<LocationFacade> {
            every { acquireKeepAlive(any()) } answers { owners += firstArg<String>() }
            every { releaseKeepAlive(any()) } answers {
                if (blockFirstRelease.compareAndSet(true, false)) {
                    releaseEntered.countDown()
                    check(allowRelease.await(2, TimeUnit.SECONDS))
                }
                owners -= firstArg<String>()
            }
        }
        val samples = mockk<LocationSampleStore>(relaxed = true) {
            every { continuousLocations } returns MutableSharedFlow()
        }
        val manager = LocationReportingManager(
            locationFacade = facade,
            locationSampleStore = samples,
            locationRepository = mockk(relaxed = true),
            clock = LocationClock(),
            ioDispatcher = Dispatchers.Unconfined,
        )
        val order = OrderKey(600L)
        manager.startReporting(order)

        val stopThread = Thread(manager::stopReporting)
        stopThread.start()
        assertTrue(releaseEntered.await(2, TimeUnit.SECONDS))
        val restartThread = Thread { manager.startReporting(order) }
        restartThread.start()
        assertTrue("restart must wait for the old release", restartThread.isAlive)
        allowRelease.countDown()
        stopThread.join(2_000)
        restartThread.join(2_000)

        assertTrue("new reporting owner must remain acquired", "location_report_600" in owners)
        manager.stopReporting()
    }

    private fun TestScope.fixture(replay: Int = 0): Fixture {
        val flow = MutableSharedFlow<LocationResult>(replay = replay)
        val facade = mockk<LocationFacade> {
            every { acquireKeepAlive(any()) } just runs
            every { releaseKeepAlive(any()) } just runs
        }
        val repository = mockk<LocationRepository>()
        val state = mockk<LocationSampleStore>(relaxed = true) {
            every { continuousLocations } returns flow
        }
        val manager = LocationReportingManager(
            locationFacade = facade,
            locationSampleStore = state,
            locationRepository = repository,
            clock = LocationClock(),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        return Fixture(flow, facade, repository, state, manager)
    }

    private fun sample(
        latitude: Double = 31.23041,
        longitude: Double = 121.47370,
        locationTime: Long = System.currentTimeMillis(),
    ) = LocationResult(
        latitude = latitude,
        longitude = longitude,
        provider = "amap_continuous",
        accuracy = 8f,
        coordType = "GCJ02",
        locationType = 5,
        trustedLevel = 2,
        locationTime = locationTime,
    )

    private data class Fixture(
        val flow: MutableSharedFlow<LocationResult>,
        val facade: LocationFacade,
        val repository: LocationRepository,
        val state: LocationSampleStore,
        val manager: LocationReportingManager,
    )
}
