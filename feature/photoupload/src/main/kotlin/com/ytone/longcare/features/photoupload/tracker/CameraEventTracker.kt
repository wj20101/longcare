package com.ytone.longcare.features.photoupload.tracker

import android.os.Build
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI

/**
 * 相机事件追踪器
 * 用于记录相机拍照流程中的关键事件，便于问题排查
 * 
 * 使用 CrashReport.postCatchedException 上报日志到 Bugly
 */
object CameraEventTracker {
    
    private const val TAG = "CameraEventTracker"
    
    /**
     * 事件类型枚举
     */
    enum class EventType(val code: String, val description: String) {
        // 相机初始化相关
        CAMERA_INIT_START("camera_init_start", "相机初始化开始"),
        CAMERA_INIT_SUCCESS("camera_init_success", "相机初始化成功"),
        CAMERA_INIT_ERROR("camera_init_error", "相机初始化失败"),
        
        // 拍照流程相关
        CAPTURE_START("capture_start", "开始拍照"),
        CAPTURE_CALLBACK_RECEIVED("capture_callback", "拍照回调接收"),
        CAPTURE_SUCCESS("capture_success", "拍照成功"),
        CAPTURE_ERROR("capture_error", "拍照失败"),
        CAPTURE_TIMEOUT("capture_timeout", "拍照处理超时"),
        
        // 图片处理相关
        IMAGE_DECODE_START("image_decode_start", "图片解码开始"),
        IMAGE_DECODE_SUCCESS("image_decode_success", "图片解码成功"),
        IMAGE_ROTATE_START("image_rotate_start", "图片旋转开始"),
        IMAGE_WATERMARK_START("image_watermark_start", "图片水印开始"),
        IMAGE_SAVE_START("image_save_start", "图片保存开始"),
        IMAGE_SAVE_SUCCESS("image_save_success", "图片保存成功"),
        IMAGE_PROCESS_ERROR("image_process_error", "图片处理失败"),
        
        // 摄像头切换相关
        CAMERA_SWITCH_START("camera_switch_start", "摄像头切换开始"),
        CAMERA_SWITCH_SUCCESS("camera_switch_success", "摄像头切换成功"),
        CAMERA_SWITCH_ERROR("camera_switch_error", "摄像头切换失败"),
        
        // 设备信息相关
        DEVICE_INFO("device_info", "设备信息"),
        LOW_END_DEVICE_DETECTED("low_end_device", "检测到低端设备"),
        
        // 权限相关
        CAMERA_PERMISSION_GRANTED("camera_permission_granted", "相机权限已授予"),
        CAMERA_PERMISSION_DENIED("camera_permission_denied", "相机权限被拒绝")
    }
    
    /**
     * 追踪事件（不带异常）
     * @param eventType 事件类型
     * @param extras 额外信息
     */
    fun trackEvent(
        eventType: EventType,
        extras: Map<String, Any?> = emptyMap()
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, null, extras)
            logI("$TAG: ${eventType.description} - $eventInfo")
            
            // 上报到 Bugly（使用自定义异常包装信息）
            val exception = CameraTrackingException(
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
     * @param throwable 异常
     * @param extras 额外信息
     */
    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap()
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, throwable, extras)
            logE("$TAG: ${eventType.description} - $eventInfo", throwable = throwable)
            
            // 上报到 Bugly
            val exception = if (throwable != null) {
                CameraTrackingException(
                    eventType = eventType.code,
                    message = eventInfo,
                    cause = throwable
                )
            } else {
                CameraTrackingException(
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
            appendLine("可用堆内存: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB")
            
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
    class CameraTrackingException(
        val eventType: String,
        message: String,
        cause: Throwable? = null
    ) : Exception("[CameraTracking:$eventType] $message", cause)
}
