package com.ytone.longcare.features.nfc.vm

import android.os.SystemClock
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.diagnostics.locationDiagnosticExtras
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.location.ServiceOrderLocationQuality
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.navigation.SignInMode
import kotlinx.coroutines.CancellationException
import java.util.UUID

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
    "accuracyBand", "requestId",
    "provider", "locationType", "trustedLevel", "isMock", "isLastLocation", "qualityReason",
    "amapErrorCode", "amapErrorInfo",
)

/** Reports each NFC acquisition once, including permission waits and fresh samples after confirmation. */
internal suspend fun requestNfcLocation(
    orderKey: OrderKey,
    mode: SignInMode,
    stage: String,
    unavailableMessage: String,
    userId: String = DiagnosticEventTracker.currentUserId(),
    reportToServer: Boolean = true,
    attemptId: String? = null,
    request: suspend () -> LocationRequestResult,
): LocationRequestResult {
    val started = SystemClock.elapsedRealtime()
    trackNfcLocation("nfc_location_request", orderKey, mode, stage, userId = userId, attemptId = attemptId)
    try {
        val result = request()
        currentCoroutineContext().ensureActive()
        val elapsedMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0)
        when (result) {
            is LocationRequestResult.Coordinates -> trackNfcLocation(
                "nfc_location_acquired", orderKey, mode, stage, location = result.location,
                durationMs = elapsedMs, userId = userId, reportToServer = reportToServer, attemptId = attemptId,
            )
            LocationRequestResult.PermissionRequired -> trackNfcLocation(
                "nfc_location_permission_required", orderKey, mode, stage,
                reason = LocationFailure.PERMISSION.name, durationMs = elapsedMs, userId = userId, reportToServer = reportToServer, attemptId = attemptId,
            )
            is LocationRequestResult.Error -> {
                if (!result.buglyReported) trackNfcLocation(
                    "nfc_location_failed", orderKey, mode, stage, isError = true,
                    reason = result.reason?.name ?: LocationFailure.UNAVAILABLE.name,
                    durationMs = elapsedMs, userId = userId, reportToServer = reportToServer, attemptId = attemptId, location = result.location,
                )
                return result.copy(buglyReported = true)
            }
        }
        return result
    } catch (cancelled: CancellationException) {
        trackNfcLocation("nfc_location_cancelled", orderKey, mode, stage, userId = userId, attemptId = attemptId)
        throw cancelled
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        trackNfcLocation(
            "nfc_location_exception", orderKey, mode, stage, isError = true, throwable = error,
            durationMs = (SystemClock.elapsedRealtime() - started).coerceAtLeast(0), userId = userId,
            reportToServer = reportToServer, attemptId = attemptId,
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
    reportToServer: Boolean = true,
    attemptId: String? = null,
) {
    val extras = linkedMapOf<String, Any?>(
        "orderId" to orderKey.orderId, "planId" to orderKey.planId,
        "signInMode" to mode.name, "stage" to stage,
        "errorCode" to reason, "durationMs" to durationMs, "attemptId" to attemptId,
    ).apply { putAll(locationFields) }
    if (isError && reportToServer) {
        DiagnosticEventTracker.trackError(
            DiagnosticCategory.LOCATION, event, "签到/签退定位异常", throwable, extras, userId,
        )
    } else {
        DiagnosticEventTracker.trackEvent(
            DiagnosticCategory.LOCATION, event, "签到/签退定位状态", extras, userId,
            reportToServer = reportToServer && event in setOf("nfc_location_acquired", "nfc_location_validated", "nfc_location_submit_success"),
        )
    }
}

/** Preserve AMap's coordinate pair and quality metadata for NFC distance diagnosis; omit address/tag. */
internal fun nfcLocationExtras(
    location: LocationResult,
    now: Long = System.currentTimeMillis(),
    elapsedRealtime: Long = SystemClock.elapsedRealtime(),
): Map<String, Any?> = locationDiagnosticExtras(location, now) + mapOf(
    "qualityReason" to ServiceOrderLocationQuality.rejection(location, now, elapsedRealtime)?.name,
    "accuracyBand" to ServiceOrderLocationQuality.accuracyBand(location.accuracy).name,
)

/** One user action, one remote outcome. Confirmation/permission waits and retries start new actions. */
internal class EndOrderDiagnostics(
    private val orderKey: OrderKey,
    private val entry: String,
    val userId: String = DiagnosticEventTracker.currentUserId(),
    val attemptId: String = UUID.randomUUID().toString(),
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private val startedAt = elapsedRealtime()
    private val durations = linkedMapOf<String, Any?>()
    private var stage = "check"
    private var locationFields: Map<String, Any?> = emptyMap()
    private var reported = false

    suspend fun <T> measure(stage: String, operation: suspend () -> T): T {
        this.stage = stage
        val startedAt = elapsedRealtime()
        DiagnosticEventTracker.trackEvent(
            NFC_DIAGNOSTIC_CATEGORY, "nfc_end_order_stage", "签退阶段开始",
            identity() + ("stage" to stage), userId,
        )
        try {
            return operation()
        } finally {
            durations["${stage}Ms"] = (elapsedRealtime() - startedAt).coerceAtLeast(0)
        }
    }

    fun captureLocation(location: LocationResult?) {
        if (location != null) locationFields = nfcLocationExtras(location).filterKeys { it in evidenceKeys }
    }

    fun finish(
        outcome: String,
        reason: String? = null,
        failure: ApiResult.Failure? = null,
        throwable: Throwable? = null,
        isError: Boolean = false,
        reportToServer: Boolean = true,
    ) {
        if (reported) return
        reported = true
        val extras = identity() + linkedMapOf(
            "outcome" to outcome,
            "stage" to stage,
            "durationMs" to (elapsedRealtime() - startedAt).coerceAtLeast(0),
            "reason" to reason,
            "failureCode" to failure?.code,
        ) + durations + locationFields + ("failureMessage" to failure?.message)
        if (isError) {
            DiagnosticEventTracker.trackError(
                NFC_DIAGNOSTIC_CATEGORY, "nfc_end_order_result", "签退结果", throwable, extras, userId,
            )
        } else {
            DiagnosticEventTracker.trackEvent(
                NFC_DIAGNOSTIC_CATEGORY, "nfc_end_order_result", "签退结果", extras, userId, reportToServer,
            )
        }
    }

    private fun identity() = linkedMapOf<String, Any?>(
        "attemptId" to attemptId, "orderId" to orderKey.orderId, "planId" to orderKey.planId,
        "signInMode" to SignInMode.END_ORDER.name, "entry" to entry,
    )

    private companion object {
        // Keep exact submitted coordinates and quality evidence within the common payload size limit.
        val evidenceKeys = setOf("longitude", "latitude", "coordType", "locationTime", "sampleAgeMs",
            "accuracy", "requestId", "locationType", "trustedLevel", "isMock", "amapErrorCode")
    }
}

internal suspend fun withEndOrderDiagnostics(
    orderKey: OrderKey,
    entry: String,
    operation: suspend (EndOrderDiagnostics) -> Unit,
) {
    val diagnostics = EndOrderDiagnostics(orderKey, entry)
    try {
        operation(diagnostics)
    } catch (cancelled: CancellationException) {
        diagnostics.finish("cancelled", reportToServer = false)
        throw cancelled
    } catch (error: Exception) {
        currentCoroutineContext().ensureActive()
        diagnostics.finish("exception", throwable = error, isError = true)
        throw error
    }
}
