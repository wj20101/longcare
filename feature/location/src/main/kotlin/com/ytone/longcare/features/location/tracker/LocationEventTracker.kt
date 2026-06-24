package com.ytone.longcare.features.location.tracker

import android.os.Build
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.model.LocationResult
import java.util.Locale

/**
 * 定位事件追踪器
 * 用于记录定位、保活、位置上报流程中的异常和错误事件，上报到 Bugly
 */
object LocationEventTracker {

    private const val TAG = "LocationEventTracker"

    enum class EventType(val code: String, val description: String) {
        // ContinuousAmapLocationManager 相关
        API_KEY_UNAVAILABLE("api_key_unavailable", "高德定位API Key不可用"),
        CLIENT_INIT_ERROR("client_init_error", "持续高德定位客户端初始化失败"),
        CLIENT_NOT_INITIALIZED("client_not_initialized", "持续高德定位客户端未初始化"),
        AMAP_CONTINUOUS_LOCATION_ERROR("amap_continuous_location_error", "持续定位失败"),
        AMAP_SINGLE_LOCATION_FAIL("amap_single_location_fail", "持续流侧单次定位获取失败"),
        ENABLE_BACKGROUND_LOCATION_ERROR("enable_background_location_error", "开启后台定位失败"),
        DISABLE_BACKGROUND_LOCATION_ERROR("disable_background_location_error", "关闭后台定位失败"),

        // SystemLocationProvider 相关
        SYSTEM_NETWORK_LOCATION_FAILED("system_network_location_failed", "系统网络定位也获取位置失败"),
        SYSTEM_LOCATION_UNAVAILABLE("system_location_unavailable", "系统GPS和网络定位均不可用"),

        // DefaultLocationFacade 相关
        AMAP_SINGLE_LOCATION_ERROR("amap_single_location_error", "高德单次定位异常"),
        SYSTEM_SINGLE_LOCATION_ERROR("system_single_location_error", "系统单次定位异常"),

        // LocationKeepAliveManager 相关
        CACHE_COLLECT_ERROR("cache_collect_error", "定位缓存采集异常"),
        KEEP_ALIVE_START_ERROR("keep_alive_start_error", "启动定位保活服务失败"),
        KEEP_ALIVE_STOP_ERROR("keep_alive_stop_error", "停止定位保活服务失败"),

        // LocationTrackingService 相关
        SERVICE_START_ERROR("service_start_error", "启动定位前台保活失败"),
        SERVICE_STOP_ERROR("service_stop_error", "停止定位前台保活失败"),

        // LocationReportingManager 相关
        REPORTING_START("reporting_start", "位置上报任务启动"),
        REPORTING_STOP("reporting_stop", "位置上报任务停止"),
        LOCATION_SAMPLE_RECORDED("location_sample_recorded", "采集到定位样本"),
        LOCATION_JUMP_DETECTED("location_jump_detected", "检测到疑似定位跳点"),
        LOCATION_STALE_SKIPPED("location_stale_skipped", "跳过陈旧定位样本"),
        REPORTING_TASK_ERROR("reporting_task_error", "位置上报任务异常终止"),
        QUEUE_WRITE_ERROR("queue_write_error", "写入定位上报队列失败"),
        API_UPLOAD_BUSINESS_ERROR("api_upload_business_error", "位置上报业务失败"),
        API_UPLOAD_NETWORK_ERROR("api_upload_network_error", "位置上报异常"),
        API_UPLOAD_FATAL_ERROR("api_upload_fatal_error", "上传位置过程发生严重错误"),
        QUEUE_CLEANUP_ERROR("queue_cleanup_error", "清理历史成功定位记录失败")
    }

    fun trackEvent(
        eventType: EventType,
        extras: Map<String, Any?> = emptyMap()
    ) {
        report(eventType, null, extras, "追踪事件失败")
    }

    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap()
    ) {
        report(eventType, throwable, extras, "追踪错误事件失败")
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

    private fun report(
        eventType: EventType,
        throwable: Throwable?,
        extras: Map<String, Any?>,
        failureMessage: String
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, throwable, extras)
            if (throwable == null) {
                logI("$TAG: ${eventType.description} - $eventInfo")
            } else {
                logE("$TAG: ${eventType.description} - $eventInfo", throwable = throwable)
            }
            CrashReport.postCatchedException(
                LocationTrackingException(
                    eventType = eventType.code,
                    message = eventInfo,
                    cause = throwable
                )
            )
        } catch (e: Exception) {
            logE("$TAG: $failureMessage - ${e.message}")
        }
    }

    private fun buildLocationExtras(
        orderId: Long,
        location: LocationResult,
        extras: Map<String, Any?>
    ): Map<String, Any?> {
        val locationExtras = linkedMapOf<String, Any?>(
            "orderId" to orderId,
            "latitude" to location.latitude.formatCoordinate(),
            "longitude" to location.longitude.formatCoordinate(),
            "provider" to location.provider,
            "accuracy" to location.accuracy,
            "coordType" to location.coordType,
            "locationType" to location.locationType,
            "trustedLevel" to location.trustedLevel,
            "locationTime" to location.locationTime
        )
        locationExtras.putAll(extras)
        return locationExtras
    }

    private fun Double.formatCoordinate(): String {
        return String.format(Locale.US, "%.5f", this)
    }

    private fun buildEventInfo(
        eventType: EventType,
        throwable: Throwable?,
        extras: Map<String, Any?>
    ): String {
        return buildString {
            appendLine("【${eventType.description}】")
            appendLine("事件码: ${eventType.code}")
            appendLine("时间戳: ${System.currentTimeMillis()}")
            
            appendLine("--- 设备信息 ---")
            appendLine("SDK版本: ${Build.VERSION.SDK_INT}")
            appendLine("厂商: ${Build.MANUFACTURER}")
            appendLine("型号: ${Build.MODEL}")
            appendLine("品牌: ${Build.BRAND}")
            
            if (extras.isNotEmpty()) {
                appendLine("--- 额外信息 ---")
                extras.forEach { (key, value) ->
                    appendLine("$key: $value")
                }
            }
            
            if (throwable != null) {
                appendLine("--- 异常信息 ---")
                appendLine("异常类型: ${throwable.javaClass.simpleName}")
                appendLine("异常消息: ${throwable.message}")
            }
        }
    }

    class LocationTrackingException(
        val eventType: String,
        message: String,
        cause: Throwable? = null
    ) : Exception("[LocationTracking:$eventType] $message", cause)
}
