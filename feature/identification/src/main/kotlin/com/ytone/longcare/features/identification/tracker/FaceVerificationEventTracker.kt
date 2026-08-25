package com.ytone.longcare.features.identification.tracker

import android.os.Build
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError

/**
 * 人脸认证事件追踪器。
 *
 * 普通事件只写本地日志，错误事件才上报 Bugly，避免诊断代码阻塞人脸业务主流程。
 * 只记录排查链路所需的阶段、错误码和大小信息，不记录照片、身份证号、姓名、密钥等敏感数据。
 */
object FaceVerificationEventTracker {
    private const val TAG = "FaceVerificationEventTracker"

    enum class EventType(val code: String, val description: String) {
        SERVICE_FACE_SETUP_REQUIRED("service_face_setup_required", "服务人员需要设置人脸"),
        SERVICE_FACE_SOURCE_ERROR("service_face_source_error", "服务人员人脸来源获取失败"),
        FACE_INIT_SUCCESS("face_init_success", "人脸验证初始化成功"),
        FACE_INIT_ERROR("face_init_error", "人脸验证初始化失败"),
        FACE_VERIFY_SUCCESS("face_verify_success", "人脸验证成功"),
        FACE_VERIFY_ERROR("face_verify_error", "人脸验证失败"),
        FACE_VERIFY_CANCELLED("face_verify_cancelled", "人脸验证取消"),
        FACE_SETUP_ERROR("face_setup_error", "人脸设置失败"),
        FACE_SETUP_UPLOAD_SUCCESS("face_setup_upload_success", "人脸设置上传成功")
    }

    fun trackEvent(
        eventType: EventType,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        report(
            eventType = eventType,
            throwable = null,
            extras = extras,
            reportToCrash = false,
        )
    }

    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        report(
            eventType = eventType,
            throwable = throwable,
            extras = extras,
            reportToCrash = true,
        )
    }

    fun faceErrorExtras(
        error: FaceVerifyError?,
        extras: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> {
        val values = LinkedHashMap<String, Any?>()
        values.putAll(extras)
        if (error != null) {
            values["errorDomain"] = error.domain
            values["errorCode"] = error.code
            values["errorDescription"] = error.description
            values["errorReason"] = error.reason
        }
        return values
    }

    private fun report(
        eventType: EventType,
        throwable: Throwable?,
        extras: Map<String, Any?>,
        reportToCrash: Boolean,
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, throwable, extras)
            if (throwable == null) {
                logI("$TAG: ${eventType.description} - $eventInfo")
            } else {
                logE("$TAG: ${eventType.description} - $eventInfo", throwable = throwable)
            }
            if (reportToCrash) {
                CrashReportGateway.postCaughtException(
                    FaceVerificationTrackingException(
                        eventType = eventType.code,
                        message = eventInfo,
                        cause = throwable,
                    ),
                )
            }
        } catch (e: Exception) {
            logE("$TAG: 上报事件失败 - ${e.message}")
        }
    }

    private fun buildEventInfo(
        eventType: EventType,
        throwable: Throwable?,
        extras: Map<String, Any?>,
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

    class FaceVerificationTrackingException(
        val eventType: String,
        message: String,
        cause: Throwable? = null,
    ) : Exception("[FaceVerificationTracking:$eventType] $message", cause)
}
