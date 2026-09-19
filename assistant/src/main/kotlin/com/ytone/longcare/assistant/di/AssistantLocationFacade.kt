package com.ytone.longcare.assistant.di

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import android.os.CancellationSignal
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationRuntimeReadiness
import com.ytone.longcare.model.LocationResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Optional one-shot watermark coordinates. Never starts the care tracking service. */
@Singleton
class AssistantLocationFacade @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationFacade, LocationRuntimeReadiness {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var cached: LocationResult? = null

    override fun hasLocationPermission() =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    override fun isLocationServiceEnabled() = LocationManagerCompat.isLocationEnabled(manager)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult? {
        if (!hasLocationPermission()) return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { manager.isProviderEnabled(it) } ?: return null
        return withTimeoutOrNull(timeoutMs.coerceIn(1_000, 15_000)) {
            suspendCancellableCoroutine { continuation ->
                val signal = CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                LocationManagerCompat.getCurrentLocation(
                    manager, provider, signal, ContextCompat.getMainExecutor(context),
                ) { location ->
                    if (continuation.isActive) continuation.resume(location?.toResult())
                }
            }
        }?.also { cached = it }
    }

    override suspend fun getFreshLocation(timeoutMs: Long) = getCurrentLocation(timeoutMs)
    override fun getCachedLocation(maxAgeMs: Long) = cached?.takeIf {
        System.currentTimeMillis() - it.locationTime in 0..maxAgeMs
    }
    // This shell only consumes one-shot location; no continuous/keep-alive owner is registered.
    override fun acquireKeepAlive(owner: String) = Unit
    override fun releaseKeepAlive(owner: String) = Unit

    private fun Location.toResult() = LocationResult(
        latitude = latitude, longitude = longitude, provider = provider.orEmpty(),
        accuracy = accuracy, coordType = "WGS84", locationTime = time,
    )
}
