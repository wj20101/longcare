package com.ytone.longcare.features.nfc.vm

import android.app.Activity
import android.content.Context
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.domain.location.LocationFacade
import kotlinx.coroutines.CancellationException

internal class NfcActivityAndLocationDelegate(
    private val context: Context,
    private val nfcManager: NfcManager,
    private val locationFacade: LocationFacade
) {
    fun isNfcSupported(): Boolean {
        return NfcUtils.isNfcSupported(context)
    }

    fun enableNfcForActivity(activity: Activity) {
        nfcManager.enableNfcForActivity(activity)
    }

    fun disableNfcForActivity(activity: Activity) {
        nfcManager.disableNfcForActivity(activity)
    }

    fun notifyLocationPermissionGranted() {
        locationFacade.notifyPermissionGranted()
    }

    suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            val location = locationFacade.getCurrentLocation()
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
