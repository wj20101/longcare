package com.ytone.longcare.features.countdown.tracker

import android.os.Build
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI

/**
 * 倒计时事件追踪器
 * 用于记录倒计时流程中的关键事件，便于问题排查
 * 
 * 使用 CrashReport.postCatchedException 上报日志到 Bugly
 */
object CountdownEventTracker {
    
    private const val TAG = "CountdownEventTracker"
    
    /**
     * 事件类型枚举
     */
    enum class EventType(val code: String, val description: String) {
        // 闹钟设置相关
        ALARM_SCHEDULE_START("alarm_schedule_start", "开始设置闹钟"),
        ALARM_SCHEDULE_SUCCESS("alarm_schedule_success", "闹钟设置成功"),
        ALARM_SCHEDULE_FAILED("alarm_schedule_failed", "闹钟设置失败"),
        ALARM_CANCEL("alarm_cancel", "取消闹钟"),
        
        // 闹钟触发相关
        ALARM_TRIGGERED("alarm_triggered", "闹钟触发"),
        ALARM_RECEIVER_ERROR("alarm_receiver_error", "闹钟接收器错误"),
        
        // 前台服务相关
        FOREGROUND_SERVICE_START("fg_service_start", "前台服务启动"),
        FOREGROUND_SERVICE_STOP("fg_service_stop", "前台服务停止"),
        FOREGROUND_SERVICE_ERROR("fg_service_error", "前台服务错误"),
        
        // 响铃服务相关
        RINGTONE_SERVICE_START("ringtone_service_start", "响铃服务启动"),
        RINGTONE_SERVICE_STOP("ringtone_service_stop", "响铃服务停止"),
        RINGTONE_SERVICE_ERROR("ringtone_service_error", "响铃服务错误"),
        
        // 通知相关
        NOTIFICATION_BUILD("notification_build", "通知构建"),
        NOTIFICATION_SHOW("notification_show", "通知显示"),
        NOTIFICATION_ERROR("notification_error", "通知错误"),
        FULLSCREEN_INTENT_DENIED("fullscreen_intent_denied", "全屏Intent权限被拒绝"),
        
        // Activity相关
        ALARM_ACTIVITY_CREATE("alarm_activity_create", "闹钟Activity创建"),
        ALARM_ACTIVITY_DESTROY("alarm_activity_destroy", "闹钟Activity销毁"),
        
        // 权限相关
        PERMISSION_CHECK("permission_check", "权限检查"),
        PERMISSION_DENIED("permission_denied", "权限被拒绝"),
        
        // 备份Worker相关
        BACKUP_WORKER_SCHEDULE("backup_worker_schedule", "备份Worker调度"),
        BACKUP_WORKER_TRIGGERED("backup_worker_triggered", "备份Worker触发"),
        BACKUP_WORKER_SKIPPED("backup_worker_skipped", "备份Worker跳过(闹钟已触发)"),
        
        // 服务结束相关
        SERVICE_END_START("service_end_start", "开始结束服务"),
        SERVICE_END_SUCCESS("service_end_success", "结束服务成功"),
        SERVICE_END_ERROR("service_end_error", "结束服务错误")
    }
    
    /**
     * 追踪事件（不带异常）
     * @param eventType 事件类型
     * @param orderId 订单ID
     * @param extras 额外信息
     */
    fun trackEvent(
        eventType: EventType,
        orderId: Long? = null,
        extras: Map<String, Any?> = emptyMap()
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, orderId, null, extras)
            logI("$TAG: ${eventType.description} - $eventInfo")
            
            // 上报到 Bugly（使用 RuntimeException 包装信息）
            val exception = CountdownTrackingException(
                eventType = eventType.code,
                message = eventInfo
            )
            CrashReport.postCatchedException(exception)
        } catch (e: Exception) {
            logE("$TAG: 追踪事件失败 - ${e.message}")
        }
    }
    
    /**
     * 追踪错误事件（带异常）
     * @param eventType 事件类型
     * @param orderId 订单ID
     * @param throwable 异常
     * @param extras 额外信息
     */
    fun trackError(
        eventType: EventType,
        orderId: Long? = null,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap()
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, orderId, throwable, extras)
            logE("$TAG: ${eventType.description} - $eventInfo", throwable = throwable)
            
            // 上报到 Bugly
            val exception = if (throwable != null) {
                CountdownTrackingException(
                    eventType = eventType.code,
                    message = eventInfo,
                    cause = throwable
                )
            } else {
                CountdownTrackingException(
                    eventType = eventType.code,
                    message = eventInfo
                )
            }
            CrashReport.postCatchedException(exception)
        } catch (e: Exception) {
            logE("$TAG: 追踪错误事件失败 - ${e.message}")
        }
    }
    
    /**
     * 构建事件信息字符串
     */
    private fun buildEventInfo(
        eventType: EventType,
        orderId: Long?,
        throwable: Throwable?,
        extras: Map<String, Any?>
    ): String {
        return buildString {
            appendLine("【${eventType.description}】")
            appendLine("事件码: ${eventType.code}")
            appendLine("时间戳: ${System.currentTimeMillis()}")
            
            if (orderId != null) {
                appendLine("订单ID: $orderId")
            }
            
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
    
    /**
     * 自定义追踪异常类
     * 用于在 Bugly 中区分追踪事件
     */
    class CountdownTrackingException(
        val eventType: String,
        message: String,
        cause: Throwable? = null
    ) : Exception("[CountdownTracking:$eventType] $message", cause)
}
