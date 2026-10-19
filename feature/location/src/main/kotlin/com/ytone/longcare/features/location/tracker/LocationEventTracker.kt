package com.ytone.longcare.features.location.tracker

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.LocationResult
import java.util.Locale

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object LocationEventTracker {
    enum class EventType(val code: String, val description: String) {
        // ContinuousAmapLocationManager 相关
        SDK_RECOVERED("sdk_recovered", "无回调后重建定位采集"),
        CLIENT_INIT_ERROR("client_init_error", "持续高德定位客户端初始化失败"),
        CLIENT_CLEANUP_ERROR("client_cleanup_error", "持续高德定位客户端释放失败"),
        AMAP_CONTINUOUS_LOCATION_ERROR("amap_continuous_location_error", "持续定位失败"),
        AMAP_CONTINUOUS_LOCATION_RECOVERED("amap_continuous_location_recovered", "高德恢复成功回调"),
        AMAP_SINGLE_LOCATION_FAIL("amap_single_location_fail", "单次定位获取失败"),

        // LocationKeepAliveManager 相关
        KEEP_ALIVE_START_ERROR("keep_alive_start_error", "启动定位保活服务失败"),
        KEEP_ALIVE_STOP_ERROR("keep_alive_stop_error", "停止定位保活服务失败"),

        // LocationTrackingService 相关
        SERVICE_START_ERROR("service_start_error", "启动定位前台保活失败"),
        SERVICE_STOP_ERROR("service_stop_error", "停止定位前台保活失败"),

        // LocationReportingManager 相关
        LOCATION_SAMPLE_RECORDED("location_sample_recorded", "采集到定位样本"),
        LOCATION_INVALID_SKIPPED("location_invalid_skipped", "跳过无效定位样本"),
        REPORTING_TASK_ERROR("reporting_task_error", "位置上报任务异常终止"),
        API_UPLOAD_BUSINESS_ERROR("api_upload_business_error", "位置上报业务失败"),
        API_UPLOAD_NETWORK_ERROR("api_upload_network_error", "位置上报异常"),
        API_UPLOAD_RESUMED("api_upload_resumed", "位置上报首次成功或恢复"),
        API_UPLOAD_FATAL_ERROR("api_upload_fatal_error", "上传位置过程发生严重错误")
    }

    /** Bugly 额外字段协议，避免上报方各自拼写键名。 */
    object Attribute {
        const val ERROR_TYPE = "errorType"
        const val ERROR_CODE = "errorCode"
        const val ORDER_ID = "orderId"
        const val PROVIDER = "provider"
        const val ACCURACY = "accuracy"
        const val COORDINATE_TYPE = "coordType"
        const val LOCATION_TYPE = "locationType"
        const val TRUSTED_LEVEL = "trustedLevel"
        const val LOCATION_TIME = "locationTime"
        const val SAMPLE_REASON = "sampleReason"
        const val CLEANUP_STAGE = "stage"

        internal const val LATITUDE = "latitude"
        internal const val LONGITUDE = "longitude"
        internal const val PREVIOUS_LATITUDE = "previouslatitude"
        internal const val PREVIOUS_LONGITUDE = "previouslongitude"
    }

    fun trackEvent(
        eventType: EventType,
        extras: Map<String, Any?> = emptyMap(),
        reportToServer: Boolean = false,
        userId: String = DiagnosticEventTracker.currentUserId(),
    ) {
        DiagnosticEventTracker.trackEvent(
            category = DiagnosticCategory.LOCATION,
            event = eventType.code,
            description = eventType.description,
            extras = extras,
            userId = userId,
            reportToServer = reportToServer,
        )
    }

    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
        userId: String = DiagnosticEventTracker.currentUserId(),
    ) {
        DiagnosticEventTracker.trackError(
            category = DiagnosticCategory.LOCATION,
            event = eventType.code,
            description = eventType.description,
            throwable = throwable,
            extras = extras,
            userId = userId,
        )
    }

    fun trackLocationSample(
        eventType: EventType,
        orderId: Long,
        location: LocationResult,
        extras: Map<String, Any?> = emptyMap()
    ) {
        trackEvent(
            eventType = eventType,
            extras = buildLocationExtras(orderId, location, extras)
        )
    }

    private fun buildLocationExtras(
        orderId: Long,
        location: LocationResult,
        extras: Map<String, Any?>
    ): Map<String, Any?> {
        val locationExtras = LinkedHashMap<String, Any?>()
        locationExtras.putAll(
            extras.filterKeys { key ->
                key.lowercase() !in PRECISE_COORDINATE_KEYS
            },
        )
        locationExtras.putAll(
            linkedMapOf(
            Attribute.ORDER_ID to orderId,
            Attribute.PROVIDER to location.provider,
            Attribute.ACCURACY to location.accuracy,
            Attribute.COORDINATE_TYPE to location.coordType,
            Attribute.LOCATION_TYPE to location.locationType,
            Attribute.TRUSTED_LEVEL to location.trustedLevel,
            Attribute.LOCATION_TIME to location.locationTime,
            "requestId" to location.requestId.takeIf { it.isNotBlank() },
            )
        )
        return locationExtras
    }

    private val PRECISE_COORDINATE_KEYS = setOf(
        Attribute.LATITUDE,
        Attribute.LONGITUDE,
        Attribute.PREVIOUS_LATITUDE,
        Attribute.PREVIOUS_LONGITUDE,
    )
}

/** One SDK client's failure episode; no timer, history queue or changes to location sampling. */
internal class ContinuousLocationDiagnostics(
    private val requestId: String,
    private val httpTimeoutMs: Long,
    private val runtimeSnapshot: () -> Map<String, Any?> = { emptyMap() },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Failure(val episode: Long, val code: Int, val startedAt: Long, var intervalMs: Long) {
        var count = 1L
        var reportedCount = 0L
        var reportedAt = startedAt
        var evidence: Map<String, Any> = emptyMap()
    }

    // Keep the acquisition's identity even if a late callback/cleanup follows an account switch.
    private val userId = DiagnosticEventTracker.currentUserId()
    private var failureEpisode = 0L
    private var failure: Failure? = null
    private var finished = false

    @Synchronized
    fun onLocation(raw: AMapLocation, intervalMs: Long) {
        if (finished) return
        val code = read { raw.errorCode } ?: return
        val now = elapsedRealtime()
        val previous = failure
        if (code == AMapLocation.LOCATION_SUCCESS) {
            if (previous != null) {
                report(previous, "recovered", now, isError = false, recovered = true)
                failure = null
            }
            return
        }
        if (previous == null || previous.code != code) {
            if (previous != null && previous.count > previous.reportedCount) {
                report(previous, "error_changed", now, isError = false)
            }
            val next = Failure(++failureEpisode, code, now, intervalMs)
            failure = next
            next.evidence = evidence(raw)
            report(next, "first", now, isError = true)
        } else {
            previous.count += 1
            previous.intervalMs = intervalMs
            // Keep the latest bounded SDK evidence for short episodes that recover before a summary.
            previous.evidence = evidence(raw)
            if (now - previous.reportedAt >= FAILURE_SUMMARY_INTERVAL_MS) {
                report(previous, "summary", now, isError = true)
            }
        }
    }

    @Synchronized
    fun finish() {
        if (finished) return
        finished = true
        failure?.takeIf { it.count > it.reportedCount }?.let {
            report(it, "finished", elapsedRealtime(), isError = false)
        }
        failure = null
    }

    private fun report(state: Failure, stage: String, now: Long, isError: Boolean, recovered: Boolean = false) {
        val type = if (recovered) LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_RECOVERED
            else LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR
        // Keep the failure identity/count before optional environment fields in the bounded payload.
        val extras = mapOf(
            "requestId" to requestId,
            "failureEpisode" to state.episode,
            LocationEventTracker.Attribute.ERROR_CODE to state.code,
            "failureCount" to state.count,
            "failureDurationMs" to (now - state.startedAt).coerceAtLeast(0L),
            "stage" to stage,
            "intervalMs" to state.intervalMs,
            "httpTimeoutMs" to httpTimeoutMs,
        ) + read(runtimeSnapshot).orEmpty() + state.evidence
        // Diagnostics must not interrupt an SDK callback or client cleanup.
        read {
            if (isError) DiagnosticEventTracker.trackError(
                DiagnosticCategory.LOCATION, type.code, type.description, extras = extras, userId = userId,
            ) else DiagnosticEventTracker.trackEvent(
                DiagnosticCategory.LOCATION, type.code, type.description, extras = extras, userId = userId,
                reportToServer = true,
            )
        }
        state.reportedAt = now
        state.reportedCount = state.count
    }

    private fun evidence(raw: AMapLocation): Map<String, Any> = buildMap {
        val detail = listOfNotNull(read { raw.errorInfo }, read { raw.locationDetail })
            .joinToString("\n") { it.take(MAX_DETAIL_LENGTH) }
        NETWORK_FAILURE.find(detail)?.groupValues?.get(1)?.let { put("networkFailureKind", it) }
        SDK_DETAIL_CODE.find(detail)?.groupValues?.get(1)?.let { put("sdkDetailCode", it) }
        read { raw.locationQualityReport }?.let { quality ->
            read { quality.networkType }?.uppercase(Locale.ROOT)?.takeIf { it in NETWORK_TYPES }
                ?.let { put("networkType", it) }
            read { quality.netUseTime }?.takeIf { it >= 0 }?.let { put("netUseTimeMs", it) }
            read { quality.isWifiAble }?.let { put("wifiAble", it) }
            read { quality.getGPSStatus() }?.let { put("gpsStatus", it) }
            read { quality.getGPSSatellites() }?.takeIf { it >= 0 }?.let { put("satellites", it) }
        }
    }

    private inline fun <T> read(block: () -> T): T? = try { block() } catch (_: Exception) { null }

    private companion object {
        /** Project diagnostic cadence; it does not change the SDK's callback/retry interval. */
        const val FAILURE_SUMMARY_INTERVAL_MS = 5 * 60_000L
        const val MAX_DETAIL_LENGTH = 4_096
        val NETWORK_TYPES = setOf("WIFI", "2G", "3G", "4G", "5G", "UNKNOWN", "DISCONNECTED")
        val NETWORK_FAILURE = Regex(
            "(?<![A-Za-z0-9_$])(UnknownHostException|SocketTimeoutException|ConnectException|" +
                "NoRouteToHostException|SSLHandshakeException|SSLPeerUnverifiedException|" +
                "SSLProtocolException|SSLException|SocketException|InterruptedIOException)(?![A-Za-z0-9_$])",
        )
        // A numeric SDK suffix is evidence only; no undocumented meaning is inferred.
        val SDK_DETAIL_CODE = Regex("#([0-9]{4})(?![0-9])")
    }
}
