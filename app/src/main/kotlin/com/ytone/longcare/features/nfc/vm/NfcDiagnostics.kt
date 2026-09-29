package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.diagnostics.locationDiagnosticExtras
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.LocationQuality
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CancellationException

private val NFC_DIAGNOSTIC_CATEGORY = DiagnosticCategory.NFC_WORKFLOW

internal fun trackNfcException(
    event: String,
    description: String,
    throwable: Throwable?,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
    userId: String = DiagnosticEventTracker.currentUserId(),
) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = event,
        description = description,
        throwable = throwable,
        extras = buildNfcExtras(orderKey, signInMode, nfcDeviceId, extras),
        userId = userId,
    )
}

internal fun trackNfcFailure(
    event: String,
    description: String,
    failure: ApiResult.Failure,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
    userId: String = DiagnosticEventTracker.currentUserId(),
) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = event,
        description = description,
        userId = userId,
        extras = buildNfcExtras(
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = mapOf(
                "failureCode" to failure.code,
                "failureMessage" to failure.message,
            ) + extras,
        ),
    )
}

internal data class NfcUserVisibleErrorReport(
    val event: String,
    val description: String,
    val extras: Map<String, Any?>,
)

internal fun reportedNfcError(message: String): NfcSignInUiState.Error =
    NfcSignInUiState.Error(
        message = message,
        buglyReported = true,
    )

internal fun buildNfcUserVisibleErrorReport(
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
): NfcUserVisibleErrorReport =
    NfcUserVisibleErrorReport(
        event = "nfc_user_visible_error",
        description = "NFC用户可见错误",
        extras = buildNfcExtras(
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = extras + mapOf(
                "source" to source,
                "message" to message,
            ),
        ),
    )

internal fun reportUserVisibleNfcError(
    message: String,
    source: String,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
    reporter: (NfcUserVisibleErrorReport) -> Unit = ::sendNfcUserVisibleErrorReport,
): NfcSignInUiState.Error {
    val report = buildNfcUserVisibleErrorReport(
        message = message,
        source = source,
        orderKey = orderKey,
        signInMode = signInMode,
        nfcDeviceId = nfcDeviceId,
        extras = extras,
    )
    reporter(report)
    return reportedNfcError(message)
}

internal fun sendNfcUserVisibleErrorReport(report: NfcUserVisibleErrorReport) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = report.event,
        description = report.description,
        extras = report.extras,
    )
}

private fun buildNfcExtras(
    orderKey: OrderKey?,
    signInMode: SignInMode?,
    nfcDeviceId: String?,
    extras: Map<String, Any?>,
): Map<String, Any?> {
    val values = LinkedHashMap<String, Any?>()
    if (orderKey != null) {
        values["orderId"] = orderKey.orderId
        values["planId"] = orderKey.planId
    }
    if (signInMode != null) {
        values["signInMode"] = signInMode.name
    }
    if (nfcDeviceId != null) {
        values["nfcDeviceIdLength"] = nfcDeviceId.length
        values["nfcDeviceIdHash"] = nfcDeviceId.hashCode()
    }
    extras.forEach { (key, value) ->
        sanitizeNfcExtra(key, value)?.let { sanitizedValue ->
            if (key !in values) {
                values[key] = sanitizedValue
            }
        }
    }
    return values
}

private fun sanitizeNfcExtra(key: String, value: Any?): Any? {
    if (!isAllowedNfcExtraKey(key)) return null
    if (key in setOf("longitude", "latitude") && (value !is Number || !value.toDouble().isFinite())) return null
    if (value is String && containsFullUrl(value)) return null
    return when (value) {
        null,
        is Boolean,
        is Byte,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double,
        is Char,
        is String -> value
        else -> null
    }
}

private fun isAllowedNfcExtraKey(key: String): Boolean {
    return key in allowedNfcExtraKeys
}

private fun containsFullUrl(value: String): Boolean = fullUrlRegex.containsMatchIn(value)

private val fullUrlRegex = Regex("""(?i)\b(?:https?://|www\.)\S+""")

private val allowedNfcExtraKeys = setOf(
    "source",
    "message",
    "signInMode",
    "scanSource",
    "stage",
    "stageName",
    "event",
    "eventName",
    "orderId",
    "planId",
    "endType",
    "failureCode",
    "failureMessage",
    "hasLongitude",
    "hasLatitude",
    "nfcDeviceIdLength",
    "nfcDeviceIdHash",
    "projectCount",
    "beginImageCount",
    "centerImageCount",
    "endImageCount",
    "longitude", "latitude", "coordType", "locationTime", "sampleAgeMs", "accuracy",
    "provider", "locationType", "trustedLevel", "isMock", "isLastLocation", "qualityReason",
    "amapErrorCode", "amapErrorInfo",
)

/** Reports each NFC acquisition once, including requests paused for permission or refreshed after expiry. */
internal suspend fun requestNfcLocation(
    orderKey: OrderKey,
    mode: SignInMode,
    stage: String,
    unavailableMessage: String,
    request: suspend () -> LocationRequestResult,
): LocationRequestResult {
    val userId = DiagnosticEventTracker.currentUserId()
    val started = System.nanoTime()
    trackNfcLocation("nfc_location_request", orderKey, mode, stage, userId = userId)
    try {
        val result = request()
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        when (result) {
            is LocationRequestResult.Coordinates -> trackNfcLocation(
                "nfc_location_acquired", orderKey, mode, stage, location = result.location,
                durationMs = elapsedMs, userId = userId,
            )
            LocationRequestResult.PermissionRequired -> trackNfcLocation(
                "nfc_location_permission_required", orderKey, mode, stage,
                reason = LocationFailure.PERMISSION.name, durationMs = elapsedMs, userId = userId,
            )
            is LocationRequestResult.Error -> {
                if (!result.buglyReported) trackNfcLocation(
                    "nfc_location_failed", orderKey, mode, stage, isError = true,
                    reason = result.reason?.name ?: LocationFailure.UNAVAILABLE.name,
                    durationMs = elapsedMs, userId = userId, location = result.location,
                )
                return result.copy(buglyReported = true)
            }
        }
        return result
    } catch (cancelled: CancellationException) {
        trackNfcLocation("nfc_location_cancelled", orderKey, mode, stage, userId = userId)
        throw cancelled
    } catch (error: Exception) {
        trackNfcLocation(
            "nfc_location_exception", orderKey, mode, stage, isError = true, throwable = error,
            durationMs = (System.nanoTime() - started) / 1_000_000, userId = userId,
        )
        return LocationRequestResult.Error(unavailableMessage, buglyReported = true, reason = LocationFailure.UNAVAILABLE)
    }
}

internal fun trackNfcLocation(
    event: String,
    orderKey: OrderKey,
    mode: SignInMode,
    stage: String,
    location: LocationResult? = null,
    isError: Boolean = false,
    reason: String? = null,
    durationMs: Long? = null,
    throwable: Throwable? = null,
    userId: String = DiagnosticEventTracker.currentUserId(),
    locationFields: Map<String, Any?> = location?.let { nfcLocationExtras(it) } ?: emptyMap(),
) {
    val extras = linkedMapOf<String, Any?>(
        "orderId" to orderKey.orderId, "planId" to orderKey.planId,
        "signInMode" to mode.name, "stage" to stage,
        "errorCode" to reason, "durationMs" to durationMs,
    ).apply { putAll(locationFields) }
    if (isError) {
        DiagnosticEventTracker.trackError(
            DiagnosticCategory.LOCATION, event, "签到/签退定位异常", throwable, extras, userId,
        )
    } else {
        DiagnosticEventTracker.trackEvent(
            DiagnosticCategory.LOCATION, event, "签到/签退定位状态", extras, userId,
            reportToServer = event in setOf("nfc_location_acquired", "nfc_location_validated", "nfc_location_submit_success"),
        )
    }
}

/** Preserve AMap's coordinate pair and quality metadata for NFC distance diagnosis; omit address/tag. */
internal fun nfcLocationExtras(
    location: LocationResult,
    now: Long = System.currentTimeMillis(),
    elapsedRealtime: Long = SystemClock.elapsedRealtime(),
): Map<String, Any?> = locationDiagnosticExtras(location, now) + mapOf(
    "qualityReason" to LocationQuality.rejection(location, now, elapsedRealtime)?.name,
)
