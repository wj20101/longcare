package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.domain.location.LocationFacade
import kotlinx.coroutines.CancellationException

internal class NfcLocationDelegate(
    private val locationFacade: LocationFacade,
) {
    fun notifyLocationPermissionGranted() {
        locationFacade.notifyPermissionGranted()
    }

    suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            val location = locationFacade.getFreshLocation()
            if (location != null) {
                Pair(location.longitude.toString(), location.latitude.toString())
            } else {
                Pair("", "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Pair("", "")
        }
    }
}
