package com.ytone.longcare.features.location.manager

import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSampleStoreLatestSampleTest {
    @Test
    fun `slow upload consumer keeps only latest pending location`() = runTest {
        val store = LocationSampleStore()
        val received = mutableListOf<LocationResult>()
        val firstConsumed = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            store.continuousLocations.take(2).collect { location ->
                received += location
                if (received.size == 1) {
                    firstConsumed.complete(Unit)
                    releaseFirst.await()
                }
            }
        }

        val first = location(1.0)
        val obsolete = location(2.0)
        val latest = location(3.0)
        store.publish(first)
        firstConsumed.await()
        store.publish(obsolete)
        store.publish(latest)
        releaseFirst.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(first, latest), received)
    }

    private fun location(latitude: Double) = LocationResult(
        latitude = latitude,
        longitude = 120.0,
        provider = "amap_continuous",
        locationTime = System.currentTimeMillis(),
    )
}
