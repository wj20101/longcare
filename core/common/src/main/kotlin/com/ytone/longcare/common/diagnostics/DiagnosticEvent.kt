package com.ytone.longcare.common.diagnostics

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.LogSanitizer
import java.util.Collections
import java.util.IdentityHashMap

/** A single immutable, bounded payload. userId is always the unmodified business ID. */
class DiagnosticException internal constructor(
    val fields: Map<String, String>,
    original: Throwable?,
) : Exception(DiagnosticPayload.encode(fields)) {
    init {
        // Explicit grouping marker, followed by real frames. Never attach the original cause:
        // Bugly 4.1.9.3 hashes a stack containing the wrapper message when a cause is present.
        val marker = StackTraceElement(
            "DiagnosticEvent.${fields.getValue("category")}",
            fields.getValue("eventCode"),
            "<diagnostic-event>",
            0,
        )
        val frames = original?.stackTrace ?: stackTrace.filterNot {
            it.className.startsWith("com.ytone.longcare.common.diagnostics.")
        }.toTypedArray()
        stackTrace = arrayOf(marker) + frames.take(40)
    }
}

internal object DiagnosticPayload {
    private val adapter = Moshi.Builder().build().adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java),
    )
    private val codePattern = Regex("[a-z][a-z0-9_]{0,63}")
    private val sensitiveKey = Regex(
        "(?i).*(token|password|secret|authorization|base64|identitycard|latitude|longitude).*",
    )
    private val privateKeys = setOf(
        "name", "username", "caregiver", "insuredperson", "address", "phone", "mobile",
        "email", "nfcdeviceid", "faceimg", "faceimgurl", "photo", "image", "url", "path",
        "apikey", "idcard", "request", "response", "payload",
    )
    private val urlPattern = Regex("(?i)https?://[^\\s\"'<>]+")
    private val reservedKeys = setOf("category", "eventCode", "level", "userId", "description", "errorType", "errorMessage")
    private val orderLocationApiEvents = setOf(
        "start_order_check_exception", "start_order_check_failure",
        "end_order_check_exception", "end_order_check_failure",
        "end_order_submit_exception", "end_order_submit_failure",
        "bind_location_exception", "bind_location_failure",
    )
    private val locationEvidenceKeys = setOf(
        "orderId", "planId", "signInMode", "stage", "errorCode", "failureCode",
        "latitude", "longitude", "coordType", "locationTime", "sampleAgeMs", "accuracy",
        "provider", "locationType", "trustedLevel", "isMock", "isLastLocation", "qualityReason", "amapErrorCode",
    )

    fun create(
        category: String,
        event: String,
        level: String,
        userId: String,
        description: String,
        throwable: Throwable?,
        extras: Map<String, Any?>,
    ): Map<String, String> {
        require(userId.toIntOrNull()?.toString() == userId) { "Invalid business user ID" }
        require(codePattern.matches(category)) { "Invalid diagnostic category" }
        require(codePattern.matches(event)) { "Invalid diagnostic event code" }
        val fields = linkedMapOf(
            "category" to category,
            "eventCode" to event,
            "level" to level,
            "userId" to userId,
            "description" to safeText(description, 120),
        )
        val orderLocation = (category == "location" &&
            (event.startsWith("nfc_location_") || event.startsWith("service_start_location_"))) ||
            (category == "nfc_workflow" && event in orderLocationApiEvents)
        if (throwable != null) {
            addIfFits(fields, "errorType", throwable.javaClass.name.take(120))
            if (!orderLocation) addIfFits(fields, "errorMessage", safeText(throwable.message.orEmpty(), 160))
        }
        // Preserve the evidence used to compare submitted points before optional, potentially long text.
        val entries = extras.entries.take(48).let { entries ->
            if (orderLocation) entries.sortedBy { if (it.key in locationEvidenceKeys) 0 else 1 } else entries
        }
        for ((key, value) in entries) {
            if (key in reservedKeys || value == null || key.length > 40) continue
            val normalized = key.lowercase().replace("_", "").replace("-", "")
            // Explicit NFC distance diagnostics need unmodified coordinate pairs for server comparison.
            val coordinate = orderLocation && key in setOf("latitude", "longitude") && value is Number && value.toDouble().isFinite()
            if ((!coordinate && sensitiveKey.matches(normalized)) || normalized in privateKeys) continue
            // Do not invoke arbitrary object toString(): models may contain credentials/images.
            if (value !is String && value !is Number && value !is Boolean && value !is Enum<*>) continue
            addIfFits(fields, key, if (coordinate) value.toString() else safeText(value.toString(), 160))
        }
        if (orderLocation && throwable != null) addIfFits(fields, "errorMessage", safeText(throwable.message.orEmpty(), 160))
        // Preserve bounded, sanitized cause/suppressed details without handing raw Throwables to SDK.
        if (throwable != null) {
            val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
            val pending = ArrayDeque<Throwable>()
            throwable.cause?.let(pending::add)
            throwable.suppressed.take(3).forEach(pending::add)
            var index = 0
            while (pending.isNotEmpty() && index < 3) {
                val error = pending.removeFirst()
                if (!seen.add(error)) continue
                addIfFits(fields, "relatedError${index++}", safeText(
                    "${error.javaClass.name}: ${error.message.orEmpty()} @ ${error.stackTrace.firstOrNull()}", 180,
                ))
                error.cause?.let(pending::add)
            }
        }
        return fields.toMap()
    }

    private fun addIfFits(fields: MutableMap<String, String>, key: String, value: String) {
        if (fields.size >= 30) return
        fields[key] = value
        // The SDK truncates Throwable.message at 1000 characters. Keep valid JSON below it.
        if (encode(fields).length > 950) fields.remove(key)
    }

    fun safeText(value: String, limit: Int): String =
        LogSanitizer.sanitize(urlPattern.replace(value.take(4096), "[url]")).take(limit)

    fun encode(fields: Map<String, String>): String = adapter.toJson(fields)

    fun decode(message: String): Map<String, String> =
        runCatching { adapter.fromJson(message).orEmpty() }.getOrDefault(emptyMap())
}
