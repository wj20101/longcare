package com.ytone.longcare.features.location.provider

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.location.LocationManagerCompat
import com.ytone.longcare.features.location.tracker.LocationEventTracker
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.location.LocationProvider
import com.ytone.longcare.model.LocationResult
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executor
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * 系统定位提供者实现
 */
class SystemLocationProvider @Inject constructor(
    private val locationManager: LocationManager,
    private val mainThreadExecutor: Executor
) : LocationProvider {

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult? = suspendCancellableCoroutine { continuation ->
        val cancellationSignal = CancellationSignal()
        
        // 设置取消回调
        continuation.invokeOnCancellation {
            cancellationSignal.cancel()
        }
        
        // 优先尝试GPS定位
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.GPS_PROVIDER)) {
            logI("正在尝试使用系统GPS获取位置...")
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.GPS_PROVIDER,
                cancellationSignal,
                mainThreadExecutor
            ) { location ->
                if (!continuation.isActive) {
                    return@getCurrentLocation
                }
                if (location != null) {
                    logI("系统GPS获取位置成功")
                    continuation.resume(mapToLocationResult(location))
                } else {
                    // GPS失败，尝试网络定位
                    logI("系统GPS获取位置失败，尝试网络定位...")
                    tryNetworkLocation(cancellationSignal, continuation)
                }
            }
        } else {
            // GPS不可用，直接尝试网络定位
            logI("系统GPS不可用，直接尝试网络定位...")
            tryNetworkLocation(cancellationSignal, continuation)
        }
    }
    
    @SuppressLint("MissingPermission")
    private fun tryNetworkLocation(
        cancellationSignal: CancellationSignal,
        continuation: CancellableContinuation<LocationResult?>
    ) {
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.NETWORK_PROVIDER)) {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                mainThreadExecutor
            ) { location ->
                if (!continuation.isActive) {
                    return@getCurrentLocation
                }
                if (location != null) {
                    logI("系统网络定位获取位置成功")
                    continuation.resume(mapToLocationResult(location))
                } else {
                    LocationEventTracker.trackError(LocationEventTracker.EventType.SYSTEM_NETWORK_LOCATION_FAILED)
                    continuation.resume(null)
                }
            }
        } else {
            LocationEventTracker.trackError(LocationEventTracker.EventType.SYSTEM_LOCATION_UNAVAILABLE)
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }
    
    override fun destroy() {
        // 系统定位不需要特殊的销毁操作
        logI("系统定位提供者已销毁")
    }

    private fun mapToLocationResult(location: Location): LocationResult {
        return LocationResult(
            latitude = location.latitude,
            longitude = location.longitude,
            provider = "system_${location.provider}",
            accuracy = location.accuracy
        )
    }
}
