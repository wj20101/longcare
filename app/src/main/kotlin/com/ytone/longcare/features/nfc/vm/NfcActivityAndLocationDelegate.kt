package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.messageRes

internal class NfcLocationDelegate(
    private val locationFacade: LocationFacade,
    private val textResolver: ResourceTextResolver,
) {
    suspend fun acquireLocation(): LocationRequestResult = when (val result = locationFacade.acquireCurrentLocation()) {
        is LocationAcquisition.Success -> LocationRequestResult.Coordinates(result.location)
        is LocationAcquisition.Failure -> LocationRequestResult.Error(
            textResolver.text(result.reason.messageRes()), reason = result.reason, location = result.location,
        )
    }
}
