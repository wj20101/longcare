package com.ytone.longcare.common.diagnostics

import com.ytone.longcare.model.LocationResult

/** Shared SDK snapshot for explicit order-location diagnostics; preserve coordinate precision. */
fun locationDiagnosticExtras(location: LocationResult, now: Long = System.currentTimeMillis()): Map<String, Any?> = linkedMapOf(
    "longitude" to location.longitude,
    "latitude" to location.latitude,
    "coordType" to location.coordType,
    "locationTime" to location.locationTime,
    "sampleAgeMs" to (now - location.locationTime).takeIf { location.locationTime > 0 && it >= 0 },
    "accuracy" to location.accuracy.takeIf { it.isFinite() },
    "requestId" to location.requestId.takeIf { it.isNotBlank() },
    "provider" to location.provider,
    "locationType" to location.locationType,
    "trustedLevel" to location.trustedLevel,
    "amapErrorCode" to location.errorCode,
    "isMock" to location.isMock,
    "isLastLocation" to location.isLastLocation,
    "amapErrorInfo" to location.errorInfo.takeIf { it.isNotBlank() },
)

/** 单位均为毫秒，保留带符号差值；issue 由业务时间校验提供，不在日志层另设规则。 */
fun locationTimeDiagnostic(location: LocationResult, now: Long, elapsedRealtime: Long, issue: String?): String {
    val elapsedAge = elapsedRealtime - location.receivedElapsedRealtime
    val sourceLag = location.sourceElapsedRealtime.takeIf { it != 0L }
        ?.let { location.receivedElapsedRealtime - it }
    return "issue=${issue ?: "none"},fix=${location.locationTime - location.receivedAt}," +
        "source=${sourceLag ?: "?"},elapsed=$elapsedAge,shift=${now - location.receivedAt - elapsedAge}"
}
