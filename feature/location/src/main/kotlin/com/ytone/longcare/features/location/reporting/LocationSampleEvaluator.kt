package com.ytone.longcare.features.location.reporting

import android.os.SystemClock
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import com.ytone.longcare.model.LocationResult
import javax.inject.Inject
import javax.inject.Singleton

/** 校验不消费样本；只有实际派发 HTTP 时才记录已消费时间，避免候选在等待期间被误判重复。 */
internal class LocationSampleEvaluator(
    private val sessionStartedAtElapsedRealtime: Long,
    private val clock: LocationClock,
) {
    private var lastSampleElapsedRealtime: Long? = null

    fun rejection(location: LocationResult): LocationQuality.Rejection? {
        val quality = ServiceOrderLocationQuality.rejection(
            location, clock.currentTimeMillis(), clock.elapsedRealtime(),
            maxAgeMs = ServiceOrderLocationQuality.REPORTING_MAX_AGE_MS,
            notBeforeElapsedRealtime = sessionStartedAtElapsedRealtime,
        )
        if (quality != null) return quality
        // 只有实际派发才消费锚点；GPS/网络切换或校时不能倒退、刷新同一个源点。
        return if (lastSampleElapsedRealtime?.let { LocationQuality.sampleElapsedRealtime(location) <= it } == true)
            LocationQuality.Rejection.ORDER else null
    }

    fun onUploadStarted(location: LocationResult) {
        lastSampleElapsedRealtime = LocationQuality.sampleElapsedRealtime(location)
    }
}

@Singleton
class LocationClock @Inject constructor() {
    fun currentTimeMillis(): Long = System.currentTimeMillis()
    fun elapsedRealtime(): Long = SystemClock.elapsedRealtime()
}
