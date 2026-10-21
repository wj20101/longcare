package com.ytone.longcare.domain.location

import com.ytone.longcare.model.LocationResult

/** 签到、签退、正式开始服务及持续上报共用的业务精度规则；数值是业务策略，非 SDK 保证。 */
object ServiceOrderLocationQuality {
    const val PREFERRED_ACCURACY_METERS = 100f
    const val MAX_ACCURACY_METERS = 200f
    const val POOR_ACCURACY_METERS = 500f
    // 持续流最多容许两个默认 30 秒采集周期的等待，避免慢上传后发送陈旧点。
    const val REPORTING_MAX_AGE_MS = 60_000L

    enum class AccuracyBand { PREFERRED, ACCEPTABLE, LOW, VERY_LOW, UNKNOWN }

    fun accuracyBand(accuracy: Float): AccuracyBand = when {
        !accuracy.isFinite() || accuracy <= 0 -> AccuracyBand.UNKNOWN
        accuracy <= PREFERRED_ACCURACY_METERS -> AccuracyBand.PREFERRED
        accuracy <= MAX_ACCURACY_METERS -> AccuracyBand.ACCEPTABLE
        accuracy <= POOR_ACCURACY_METERS -> AccuracyBand.LOW
        else -> AccuracyBand.VERY_LOW
    }

    fun rejection(
        sample: LocationResult,
        now: Long,
        elapsedRealtime: Long,
        notBefore: Long = 0L,
        previousSampleTime: Long = 0L,
        maxAgeMs: Long? = null,
        notBeforeElapsedRealtime: Long = 0L,
    ): LocationQuality.Rejection? = LocationQuality.rejection(
        sample, now, elapsedRealtime, notBefore, previousSampleTime,
        maxAgeMs = maxAgeMs, maxAccuracyMeters = MAX_ACCURACY_METERS,
        allowNormalTrust = true, notBeforeElapsedRealtime = notBeforeElapsedRealtime,
    )

    fun failure(sample: LocationResult, rejection: LocationQuality.Rejection): LocationFailure =
        if (rejection == LocationQuality.Rejection.ACCURACY && sample.accuracy.isFinite() &&
            sample.accuracy > MAX_ACCURACY_METERS
        ) LocationFailure.PRECISION else LocationFailure.QUALITY
}
