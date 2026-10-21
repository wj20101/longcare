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
        notBeforeElapsedRealtime: Long = 0L,
    ): Rejection? {
        if (sample.errorCode != 0) return Rejection.SDK_ERROR
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0
        ) return Rejection.COORDINATES
        if (sample.locationTime <= 0L || sample.receivedAt <= 0L ||
            sample.receivedElapsedRealtime < 0L || sample.locationTime > sample.receivedAt ||
            sample.sourceElapsedRealtime < 0L || sample.sourceElapsedRealtime > sample.receivedElapsedRealtime ||
            now < sample.receivedAt || elapsedRealtime < sample.receivedElapsedRealtime
        ) return Rejection.TIME
        // SDK 复用结果时可能刷新墙上时间；有效的源时钟仍须满足原有时效和动作时间要求。
        val sourceAgeAtReceipt = if (sample.sourceElapsedRealtime > 0L) {
            sample.receivedElapsedRealtime - sample.sourceElapsedRealtime
        } else 0L
        val ageAtReceipt = maxOf(sample.receivedAt - sample.locationTime, sourceAgeAtReceipt)
        val elapsedAge = elapsedRealtime - sample.receivedElapsedRealtime
        if (maxAgeMs != null && (ageAtReceipt > maxAgeMs || elapsedAge > maxAgeMs - ageAtReceipt ||
            now - sample.locationTime > maxAgeMs)
        ) return Rejection.STALE
        if (sample.locationTime < notBefore || sample.locationTime <= previousSampleTime ||
            sample.receivedAt - sourceAgeAtReceipt < notBefore ||
            // 动作期间校时不能把动作前的旧源点变成新点；未知源时钟沿用墙上时间检查。
            (sample.sourceElapsedRealtime > 0L && sample.sourceElapsedRealtime < notBeforeElapsedRealtime)
        ) return Rejection.ORDER
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
