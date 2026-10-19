package com.ytone.longcare.features.photoupload.tracker

import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object CameraEventTracker {
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

    fun trackEvent(
        eventType: EventType,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        DiagnosticEventTracker.trackEvent(
            category = DiagnosticCategory.CAMERA,
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
            category = DiagnosticCategory.CAMERA,
            event = eventType.code,
            description = eventType.description,
            throwable = throwable,
            extras = extras,
        )
    }
}
