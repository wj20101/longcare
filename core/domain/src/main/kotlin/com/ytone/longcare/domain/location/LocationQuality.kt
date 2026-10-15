package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 只检查提供方元数据，不计算业务距离、位移或速度，不修改原始坐标。 */
object LocationQuality {
    // 项目自定的样本年龄上限，非高德规定的业务提交时限；订单隔离不依赖此值。
    const val MAX_AGE_MS = 15_000L

    enum class Rejection { SDK_ERROR, COORDINATES, TIME, STALE, ORDER, ACCURACY, SOURCE, TRUST, MOCK, COORDINATE_TYPE }

    fun rejection(
        sample: LocationResult,
        now: Long,
        elapsedRealtime: Long,
        notBefore: Long = 0L,
        previousSampleTime: Long = 0L,
        maxAgeMs: Long? = MAX_AGE_MS,
        maxAccuracyMeters: Float? = null,
        allowNormalTrust: Boolean = false,
    ): Rejection? {
        if (sample.errorCode != 0) return Rejection.SDK_ERROR
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0
        ) return Rejection.COORDINATES
        if (sample.locationTime <= 0L || sample.receivedAt <= 0L ||
            sample.receivedElapsedRealtime < 0L || sample.locationTime > sample.receivedAt ||
            now < sample.receivedAt || elapsedRealtime < sample.receivedElapsedRealtime
        ) return Rejection.TIME
        val ageAtReceipt = sample.receivedAt - sample.locationTime
        val elapsedAge = elapsedRealtime - sample.receivedElapsedRealtime
        if (maxAgeMs != null && (ageAtReceipt > maxAgeMs || elapsedAge > maxAgeMs - ageAtReceipt ||
            now - sample.locationTime > maxAgeMs)
        ) return Rejection.STALE
        if (sample.locationTime < notBefore || sample.locationTime <= previousSampleTime) return Rejection.ORDER
        if (!sample.accuracy.isFinite() || sample.accuracy <= 0f) return Rejection.ACCURACY
        if (sample.isMock) return Rejection.MOCK
        // GPS、同请求、Wi-Fi、基站和在线网络定位；拒绝缓存、离线、最后位置和粗略定位。
        if (sample.isLastLocation || sample.locationType !in CURRENT_SOURCES) return Rejection.SOURCE
        if (sample.trustedLevel != 1 && !(allowNormalTrust && sample.trustedLevel == 2)) return Rejection.TRUST
        if (sample.coordType != "GCJ02") return Rejection.COORDINATE_TYPE
        if (maxAccuracyMeters != null && sample.accuracy > maxAccuracyMeters) return Rejection.ACCURACY
        return null
    }

    private val CURRENT_SOURCES = setOf(1, 2, 5, 6, 7, 12)
}
