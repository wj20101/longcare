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
    values.putAll(extras)
    return values
}
