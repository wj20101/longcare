package com.ytone.longcare.features.nfc.vm

import android.content.Context
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.model.LocationResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NfcActivityAndLocationDelegateTest {

    @Test
    fun `notifyLocationPermissionGranted restarts location facade after permission grant`() {
        val locationFacade = mockk<LocationFacade>(relaxed = true)
        val delegate = NfcActivityAndLocationDelegate(
            context = mockk<Context>(relaxed = true),
            nfcManager = mockk<NfcManager>(relaxed = true),
            locationFacade = locationFacade,
        )

        delegate.notifyLocationPermissionGranted()

        verify(exactly = 1) { locationFacade.notifyPermissionGranted() }
    }

    @Test
    fun `getCurrentLocationCoordinates uses fresh location for NFC`() = runTest {
        val locationFacade = mockk<LocationFacade>(relaxed = true)
        val delegate = NfcActivityAndLocationDelegate(
            context = mockk<Context>(relaxed = true),
            nfcManager = mockk<NfcManager>(relaxed = true),
            locationFacade = locationFacade,
        )
        coEvery { locationFacade.getFreshLocation(any()) } returns LocationResult(
            latitude = 31.2304,
            longitude = 121.4737,
            provider = "amap_fresh",
            accuracy = 8f
        )

        val coordinates = delegate.getCurrentLocationCoordinates()

        assertEquals(Pair("121.4737", "31.2304"), coordinates)
        coVerify(exactly = 1) {
            locationFacade.getFreshLocation(LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS)
        }
        coVerify(exactly = 0) { locationFacade.getCurrentLocation(any()) }
    }

    @Test
    fun `getCurrentLocationCoordinates returns blank coordinates when fresh location is unavailable`() = runTest {
        val locationFacade = mockk<LocationFacade>(relaxed = true)
        val delegate = NfcActivityAndLocationDelegate(
            context = mockk<Context>(relaxed = true),
            nfcManager = mockk<NfcManager>(relaxed = true),
            locationFacade = locationFacade,
        )
        coEvery { locationFacade.getFreshLocation(any()) } returns null

        val coordinates = delegate.getCurrentLocationCoordinates()

        assertEquals(Pair("", ""), coordinates)
        coVerify(exactly = 1) {
            locationFacade.getFreshLocation(LocationFacade.DEFAULT_FRESH_LOCATION_TIMEOUT_MS)
        }
    }
}
