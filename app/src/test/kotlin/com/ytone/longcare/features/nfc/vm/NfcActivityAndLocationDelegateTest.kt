package com.ytone.longcare.features.nfc.vm

import android.content.Context
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.domain.location.LocationFacade
import io.mockk.mockk
import io.mockk.verify
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
}
