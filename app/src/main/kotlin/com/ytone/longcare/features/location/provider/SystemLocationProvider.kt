package com.ytone.longcare.features.location.provider

import android.annotation.SuppressLint
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
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
                if (location != null) {
                    logI("系统GPS获取位置成功")
                    continuation.resume(LocationResult.fromSystemLocation(location))
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
        continuation: kotlin.coroutines.Continuation<LocationResult?>
    ) {
        if (LocationManagerCompat.hasProvider(locationManager, LocationManager.NETWORK_PROVIDER)) {
            LocationManagerCompat.getCurrentLocation(
                locationManager,
                LocationManager.NETWORK_PROVIDER,
                cancellationSignal,
                mainThreadExecutor
            ) { location ->
                if (location != null) {
                    logI("系统网络定位获取位置成功")
                    continuation.resume(LocationResult.fromSystemLocation(location))
                } else {
                    logE("系统网络定位也获取位置失败")
                    continuation.resume(null)
                }
            }
        } else {
            logE("系统GPS和网络定位均不可用")
            continuation.resume(null)
        }
    }
    
    override fun destroy() {
        // 系统定位不需要特殊的销毁操作
        logI("系统定位提供者已销毁")
    }
}