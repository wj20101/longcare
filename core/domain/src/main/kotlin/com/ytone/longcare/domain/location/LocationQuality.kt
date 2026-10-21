package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 只检查提供方元数据，不计算业务距离、位移或速度，不修改原始坐标。 */
object LocationQuality {
    // 项目自定的样本年龄上限，非高德规定的业务提交时限；订单隔离不依赖此值。
    const val MAX_AGE_MS = 15_000L

    enum class Rejection { SDK_ERROR, COORDINATES, TIME, STALE, ORDER, ACCURACY, SOURCE, TRUST, MOCK, COORDINATE_TYPE }

    enum class TimeIssue {
        FIX_MISSING, RECEIPT_MISSING, RECEIPT_ELAPSED_INVALID, SOURCE_INVALID,
        SOURCE_AFTER_RECEIPT, ELAPSED_BEFORE_RECEIPT, FIX_AFTER_RECEIPT, WALL_BEFORE_RECEIPT,
    }

    /** GPS UTC 与手机墙钟不是同一时钟；有源 tick 时不以两者偏差拒绝定位。 */
    fun timeIssue(sample: LocationResult, now: Long, elapsedRealtime: Long): TimeIssue? = when {
        sample.locationTime <= 0L -> TimeIssue.FIX_MISSING
        sample.receivedAt <= 0L -> TimeIssue.RECEIPT_MISSING
        sample.receivedElapsedRealtime < 0L -> TimeIssue.RECEIPT_ELAPSED_INVALID
        sample.sourceElapsedRealtime < 0L -> TimeIssue.SOURCE_INVALID
        sample.sourceElapsedRealtime > sample.receivedElapsedRealtime -> TimeIssue.SOURCE_AFTER_RECEIPT
        elapsedRealtime < sample.receivedElapsedRealtime -> TimeIssue.ELAPSED_BEFORE_RECEIPT
        sample.sourceElapsedRealtime > 0L -> null
        sample.locationTime > sample.receivedAt -> TimeIssue.FIX_AFTER_RECEIPT
        now < sample.receivedAt -> TimeIssue.WALL_BEFORE_RECEIPT
        else -> null
    }

    /** 已通过时间校验的样本锚点；未知源时间只用接收时墙钟差推算，不能刷新旧点。 */
    fun sampleElapsedRealtime(sample: LocationResult): Long =
        if (sample.sourceElapsedRealtime > 0L) sample.sourceElapsedRealtime
        else sample.receivedElapsedRealtime - (sample.receivedAt - sample.locationTime)

    fun rejection(
        sample: LocationResult,
        now: Long,
        elapsedRealtime: Long,
        maxAgeMs: Long? = MAX_AGE_MS,
        maxAccuracyMeters: Float? = null,
        allowNormalTrust: Boolean = false,
        notBeforeElapsedRealtime: Long? = null,
    ): Rejection? {
        if (sample.errorCode != 0) return Rejection.SDK_ERROR
        if (!sample.latitude.isFinite() || sample.latitude !in -90.0..90.0 ||
            !sample.longitude.isFinite() || sample.longitude !in -180.0..180.0
        ) return Rejection.COORDINATES
        if (timeIssue(sample, now, elapsedRealtime) != null) return Rejection.TIME
        val ageAtReceipt = if (sample.sourceElapsedRealtime > 0L) {
            sample.receivedElapsedRealtime - sample.sourceElapsedRealtime
        } else sample.receivedAt - sample.locationTime
        val elapsedAge = elapsedRealtime - sample.receivedElapsedRealtime
        if (maxAgeMs != null && (ageAtReceipt > maxAgeMs || elapsedAge > maxAgeMs - ageAtReceipt)
        ) return Rejection.STALE
        // null 表示未指定起点；0 是有效的开机边界，仍须拒绝推算到开机前的旧点。
        if (notBeforeElapsedRealtime != null && sampleElapsedRealtime(sample) < notBeforeElapsedRealtime)
            return Rejection.ORDER
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
