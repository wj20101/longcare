package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.SignInMode

private const val NFC_DIAGNOSTIC_CATEGORY = "nfc_workflow"

internal fun trackNfcException(
    event: String,
    description: String,
    throwable: Throwable?,
    orderKey: OrderKey? = null,
    signInMode: SignInMode? = null,
    nfcDeviceId: String? = null,
    extras: Map<String, Any?> = emptyMap(),
) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = event,
        description = description,
        throwable = throwable,
        extras = buildNfcExtras(orderKey, signInMode, nfcDeviceId, extras),
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
) {
    DiagnosticEventTracker.trackError(
        category = NFC_DIAGNOSTIC_CATEGORY,
        event = event,
        description = description,
        extras = buildNfcExtras(
            orderKey = orderKey,
            signInMode = signInMode,
            nfcDeviceId = nfcDeviceId,
            extras = extras + mapOf(
                "failureCode" to failure.code,
                "failureMessage" to failure.message,
            ),
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
)
