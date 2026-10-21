package com.ytone.longcare.features.location.reporting

import android.os.SystemClock
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

/** 校验不消费样本；只有实际派发 HTTP 时才记录已消费时间，避免候选在等待期间被误判重复。 */
internal class LocationSampleEvaluator(
    private val sessionStartedAt: Long,
    private val clock: LocationClock,
) {
    private var lastSampleTime = 0L
    private var lastSourceElapsedRealtime = 0L

    fun rejection(location: LocationResult): LocationQuality.Rejection? {
        val quality = ServiceOrderLocationQuality.rejection(
            location, clock.currentTimeMillis(), clock.elapsedRealtime(), notBefore = sessionStartedAt,
            previousSampleTime = lastSampleTime, maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
        )
        if (quality != null) return quality
        // SDK 可能刷新旧点的 UTC/time；同一来源 tick 只能在本订单采集阶段派发一次。
        return if (location.sourceElapsedRealtime > 0L && lastSourceElapsedRealtime > 0L &&
            location.sourceElapsedRealtime <= lastSourceElapsedRealtime) LocationQuality.Rejection.ORDER else null
    }

    fun onUploadStarted(location: LocationResult) {
        lastSampleTime = location.locationTime
        if (location.sourceElapsedRealtime > 0L) {
            lastSourceElapsedRealtime = maxOf(lastSourceElapsedRealtime, location.sourceElapsedRealtime)
        }
    }
}

@Singleton
class LocationClock @Inject constructor() {
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
