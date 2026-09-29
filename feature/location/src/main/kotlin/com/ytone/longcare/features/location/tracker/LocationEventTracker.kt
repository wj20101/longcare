package com.ytone.longcare.features.location.tracker

import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.model.LocationResult

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object LocationEventTracker {
    enum class EventType(val code: String, val description: String) {
        // ContinuousAmapLocationManager 相关
        API_KEY_UNAVAILABLE("api_key_unavailable", "高德定位API Key不可用"),
        CLIENT_INIT_ERROR("client_init_error", "持续高德定位客户端初始化失败"),
        CLIENT_NOT_INITIALIZED("client_not_initialized", "持续高德定位客户端未初始化"),
        AMAP_CONTINUOUS_LOCATION_ERROR("amap_continuous_location_error", "持续定位失败"),
        AMAP_SINGLE_LOCATION_FAIL("amap_single_location_fail", "单次定位获取失败"),
        ENABLE_BACKGROUND_LOCATION_ERROR("enable_background_location_error", "开启后台定位失败"),
        DISABLE_BACKGROUND_LOCATION_ERROR("disable_background_location_error", "关闭后台定位失败"),

        // LocationKeepAliveManager 相关
        KEEP_ALIVE_START_ERROR("keep_alive_start_error", "启动定位保活服务失败"),
        KEEP_ALIVE_STOP_ERROR("keep_alive_stop_error", "停止定位保活服务失败"),

        // LocationTrackingService 相关
        SERVICE_START_ERROR("service_start_error", "启动定位前台保活失败"),
        SERVICE_STOP_ERROR("service_stop_error", "停止定位前台保活失败"),

        // LocationReportingManager 相关
        REPORTING_START("reporting_start", "位置上报任务启动"),
        REPORTING_STOP("reporting_stop", "位置上报任务停止"),
        LOCATION_SAMPLE_RECORDED("location_sample_recorded", "采集到定位样本"),
        LOCATION_INVALID_SKIPPED("location_invalid_skipped", "跳过无效定位样本"),
        REPORTING_TASK_ERROR("reporting_task_error", "位置上报任务异常终止"),
        API_UPLOAD_BUSINESS_ERROR("api_upload_business_error", "位置上报业务失败"),
        API_UPLOAD_NETWORK_ERROR("api_upload_network_error", "位置上报异常"),
        API_UPLOAD_FATAL_ERROR("api_upload_fatal_error", "上传位置过程发生严重错误")
    }

    /** Bugly 额外字段协议，避免上报方各自拼写键名。 */
    object Attribute {
        const val ERROR_TYPE = "errorType"
        const val ERROR_CODE = "errorCode"
        const val ORDER_ID = "orderId"
        const val GENERATION = "generation"
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
    ) {
        DiagnosticEventTracker.trackEvent(
            category = DiagnosticCategory.LOCATION,
            event = eventType.code,
            description = eventType.description,
            extras = extras,
        )
    }

    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        DiagnosticEventTracker.trackError(
            category = DiagnosticCategory.LOCATION,
            event = eventType.code,
            description = eventType.description,
            throwable = throwable,
            extras = extras,
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
