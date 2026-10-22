package com.ytone.longcare.features.location.tracker

import android.os.SystemClock
import com.amap.api.location.AMapLocation
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import java.util.Locale

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object LocationEventTracker {
    enum class EventType(val code: String, val description: String) {
        // ContinuousAmapLocationManager 相关
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
        REPORTING_TASK_ERROR("reporting_task_error", "位置上报任务异常终止"),
        API_UPLOAD_BUSINESS_ERROR("api_upload_business_error", "位置上报业务失败"),
        API_UPLOAD_NETWORK_ERROR("api_upload_network_error", "位置上报异常"),
        REPORTING_HEALTH("reporting_health", "定位上报间隔诊断"),
        SERVICE_ACTION_RESULT("service_action_result", "服务动作定位结果诊断"),
        API_UPLOAD_RESUMED("api_upload_resumed", "位置上报首次成功"),
        API_UPLOAD_FATAL_ERROR("api_upload_fatal_error", "上传位置过程发生严重错误")
    }

    /** Bugly 额外字段协议，避免上报方各自拼写键名。 */
    object Attribute {
        const val ERROR_TYPE = "errorType"
        const val ERROR_CODE = "errorCode"
        const val ORDER_ID = "orderId"
        const val CLEANUP_STAGE = "stage"

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
}

/** One SDK client's bounded diagnostic window; callbacks never change the acquisition lifecycle. */
internal class ContinuousLocationDiagnostics(
    private val requestId: String,
    private val httpTimeoutMs: Long,
    private val runtimeSnapshot: () -> Map<String, Any?> = { emptyMap() },
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Failure(val startedAt: Long) {
        var code = AMapLocation.LOCATION_SUCCESS
        var intervalMs = 0L
        var count = 0L
        var recoveries = 0L
        var failing = false
        var reportedChanges = 0L
        var reportedAt = startedAt
        var reportSequence = 0L
        val errorCounts = linkedMapOf<Int, Long>()
        var otherErrorCount = 0L
        var evidence: Map<String, Any> = emptyMap()

        val hasUnreportedChanges get() = count + recoveries > reportedChanges

        fun recordError(errorCode: Int) {
            code = errorCode
            count += 1L
            failing = true
            if (errorCode in errorCounts || errorCounts.size < MAX_ERROR_CODES) {
                errorCounts[errorCode] = (errorCounts[errorCode] ?: 0L) + 1L
            } else {
                otherErrorCount += 1L
            }
        }

        fun errorCountsSummary(): String = buildList {
            errorCounts.forEach { (code, count) -> add("$code:$count") }
            if (otherErrorCount > 0L) add("other:$otherErrorCount")
        }.joinToString(",")
    }

    // Keep the acquisition's identity even if a late callback/cleanup follows an account switch.
    private val userId = read { DiagnosticEventTracker.currentUserId() } ?: "0"
    private var failure: Failure? = null
    private var finished = false

    @Synchronized
    fun onLocation(raw: AMapLocation, intervalMs: Long) {
        if (finished) return
        val code = read { raw.errorCode } ?: return
        val now = read(elapsedRealtime) ?: return
        val state = failure ?: if (code != AMapLocation.LOCATION_SUCCESS) {
            Failure(now).also { failure = it }
        } else return
        state.intervalMs = intervalMs
        if (code == AMapLocation.LOCATION_SUCCESS) {
            if (state.failing) state.recoveries += 1L
            state.failing = false
        } else {
            state.recordError(code)
            // Keep only the latest allowlisted SDK cause, never raw SDK text or a history queue.
            state.evidence = evidence(raw)
        }
        when {
            state.reportSequence == 0L -> report(state, "first", now)
            state.hasUnreportedChanges && now - state.reportedAt >= FAILURE_SUMMARY_INTERVAL_MS ->
                report(state, "summary", now)
        }
    }

    @Synchronized
    fun finish() {
        if (finished) return
        finished = true
        failure?.takeIf { it.hasUnreportedChanges }?.let {
            report(it, "finished", read(elapsedRealtime) ?: it.reportedAt)
        }
        failure = null
    }

    private fun report(state: Failure, stage: String, now: Long) {
        val type = if (state.failing) LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_ERROR
            else LocationEventTracker.EventType.AMAP_CONTINUOUS_LOCATION_RECOVERED
        // Report-window identity stays monotonic even when recovery changes no failure count.
        // Common diagnostic de-duplication uses requestId/failureEpisode/failureCount.
        state.reportSequence += 1L
        val extras = mapOf(
            "requestId" to requestId,
            "failureEpisode" to state.reportSequence,
            LocationEventTracker.Attribute.ERROR_CODE to state.code,
            "failureCount" to state.count,
            "recoveryCount" to state.recoveries,
            "errorCounts" to state.errorCountsSummary(),
            // The aggregate survives successful callbacks; this is observation time, not outage time.
            "observationDurationMs" to (now - state.startedAt).coerceAtLeast(0L),
            "stage" to stage,
        ) + state.evidence.filterKeys { it == "networkFailureKind" || it == "sdkDetailCode" } + mapOf(
            "intervalMs" to state.intervalMs,
            "httpTimeoutMs" to httpTimeoutMs,
        ) + read(runtimeSnapshot).orEmpty() + state.evidence
        // Diagnostics must not interrupt an SDK callback or client cleanup. Count attempted reports
        // as settled too: unavailable diagnostics must not cause a callback-by-callback retry storm.
        read {
            if (state.failing && stage != "finished") DiagnosticEventTracker.trackError(
                DiagnosticCategory.LOCATION, type.code, type.description, extras = extras, userId = userId,
            ) else DiagnosticEventTracker.trackEvent(
                DiagnosticCategory.LOCATION, type.code, type.description, extras = extras, userId = userId,
                reportToServer = true,
            )
        }
        state.reportedAt = now
        state.reportedChanges = state.count + state.recoveries
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
        // Four Int error codes plus Long counts and the overflow bucket fit the 160-char scalar cap.
        const val MAX_ERROR_CODES = 4
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
