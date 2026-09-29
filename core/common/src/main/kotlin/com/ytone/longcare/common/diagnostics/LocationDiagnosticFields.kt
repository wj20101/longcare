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
    "provider" to location.provider,
    "locationType" to location.locationType,
    "trustedLevel" to location.trustedLevel,
    "amapErrorCode" to location.errorCode,
    "isMock" to location.isMock,
    "isLastLocation" to location.isLastLocation,
    "amapErrorInfo" to location.errorInfo.takeIf { it.isNotBlank() },
)
