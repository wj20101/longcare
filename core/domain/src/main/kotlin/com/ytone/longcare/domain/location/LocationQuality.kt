package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 只检查提供方元数据，不计算业务距离、位移或速度，不修改原始坐标。 */
object LocationQuality {
    // 高德 TRUSTED_LEVEL_HIGH 表示 15 秒以内的实时结果；保留原始时间，不给缓存续期。
    const val MAX_AGE_MS = 15_000L

    enum class Rejection { COORDINATES, TIME, STALE, ORDER, ACCURACY, SOURCE, TRUST, MOCK, COORDINATE_TYPE }

    fun rejection(
        sample: LocationResult,
        now: Long,
        elapsedRealtime: Long,
        notBefore: Long = 0L,
        previousSampleTime: Long = 0L,
    ): Rejection? {
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0
        ) return Rejection.COORDINATES
        if (sample.locationTime <= 0L || sample.receivedAt <= 0L ||
            sample.receivedElapsedRealtime < 0L || sample.locationTime > sample.receivedAt ||
            now < sample.receivedAt || elapsedRealtime < sample.receivedElapsedRealtime
        ) return Rejection.TIME
        val ageAtReceipt = sample.receivedAt - sample.locationTime
        val elapsedAge = elapsedRealtime - sample.receivedElapsedRealtime
        if (ageAtReceipt > MAX_AGE_MS || elapsedAge > MAX_AGE_MS - ageAtReceipt ||
            now - sample.locationTime > MAX_AGE_MS
        ) return Rejection.STALE
        if (sample.locationTime < notBefore || sample.locationTime <= previousSampleTime) return Rejection.ORDER
        if (!sample.accuracy.isFinite() || sample.accuracy <= 0f) return Rejection.ACCURACY
        if (sample.isMock) return Rejection.MOCK
        // GPS、同请求、Wi-Fi、基站和在线网络定位；拒绝缓存、离线、最后位置和粗略定位。
        if (sample.isLastLocation || sample.locationType !in CURRENT_SOURCES) return Rejection.SOURCE
        if (sample.trustedLevel != 1) return Rejection.TRUST
        if (sample.coordType != "GCJ02") return Rejection.COORDINATE_TYPE
        return null
    }

    private val CURRENT_SOURCES = setOf(1, 2, 5, 6, 7, 12)
}
