package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.location.*
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.messageRes
import com.ytone.longcare.model.LocationResult
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NfcActivityAndLocationDelegateTest {
    private val facade = mockk<LocationFacade>()
    private val text = mockk<ResourceTextResolver> {
        every { text(any(), *anyVararg()) } answers { firstArg<Int>().toString() }
    }
    private val delegate = NfcLocationDelegate(facade, text)

    @Test fun `NFC retains immutable sample including capture metadata`() = runTest {
        val sample = LocationResult(31.0, 121.0, "network", locationTime = 1234)
        coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Success(sample)
        assertEquals(LocationRequestResult.Coordinates(sample), delegate.acquireLocation())
        coVerify(exactly = 1) { facade.acquireCurrentLocation() }
    }

    @Test fun `all acquisition failures retain specific user message and no blank coordinate success`() = runTest {
        LocationFailure.entries.forEach { reason ->
            coEvery { facade.acquireCurrentLocation() } returns LocationAcquisition.Failure(reason)
            assertEquals(LocationRequestResult.Error(reason.messageRes().toString()), delegate.acquireLocation())
        }
    }

    @Test fun `cancellation stays cancellation`() = runTest {
        coEvery { facade.acquireCurrentLocation() } throws CancellationException()
        try {
            delegate.acquireLocation()
            fail("expected cancellation")
        } catch (cancelled: CancellationException) {
            assertNotNull(cancelled)
        }
    }
}
