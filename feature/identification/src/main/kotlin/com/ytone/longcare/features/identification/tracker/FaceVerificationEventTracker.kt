package com.ytone.longcare.features.identification.tracker

import android.os.Build
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError
import java.net.URI

/**
 * 人脸认证事件追踪器。
 *
 * 只记录排查链路所需的阶段、错误码和大小信息，不上报照片、身份证号、姓名、密钥等敏感数据。
 */
object FaceVerificationEventTracker {
    private const val TAG = "FaceVerificationEventTracker"

    enum class EventType(val code: String, val description: String) {
        SERVICE_REMOTE_FACE_SELECTED("service_remote_face_selected", "服务人员使用服务端人脸照片"),
        SERVICE_FACE_SETUP_REQUIRED("service_face_setup_required", "服务人员需要设置人脸"),
        SERVICE_FACE_SOURCE_ERROR("service_face_source_error", "服务人员人脸来源获取失败"),
        REMOTE_FACE_DOWNLOAD_SUCCESS("remote_face_download_success", "服务端人脸照片下载成功"),
        REMOTE_FACE_DOWNLOAD_ERROR("remote_face_download_error", "服务端人脸照片下载失败"),
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
        report(eventType = eventType, throwable = null, extras = extras)
    }

    fun trackError(
        eventType: EventType,
        throwable: Throwable? = null,
        extras: Map<String, Any?> = emptyMap(),
    ) {
        report(eventType = eventType, throwable = throwable, extras = extras)
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

    fun safeUrlExtras(url: String): Map<String, Any?> {
        return try {
            val uri = URI(url)
            mapOf(
                "urlScheme" to uri.scheme,
                "urlHost" to uri.host,
                "urlPathLength" to (uri.path?.length ?: 0),
            )
        } catch (_: Exception) {
            mapOf(
                "urlValid" to false,
                "urlLength" to url.length,
            )
        }
    }

    private fun report(
        eventType: EventType,
        throwable: Throwable?,
        extras: Map<String, Any?>,
    ) {
        try {
            val eventInfo = buildEventInfo(eventType, throwable, extras)
            if (throwable == null) {
                logI("$TAG: ${eventType.description} - $eventInfo")
            } else {
                logE("$TAG: ${eventType.description} - $eventInfo", throwable = throwable)
            }
            CrashReportGateway.postCaughtException(
                FaceVerificationTrackingException(
                    eventType = eventType.code,
                    message = eventInfo,
                    cause = throwable,
                )
            )
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
