package com.ytone.longcare.features.identification.tracker

import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.faceauth.model.FaceVerifyError

/** Business event catalog; policy and delivery are owned by DiagnosticEventTracker. */
object FaceVerificationEventTracker {
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
        DiagnosticEventTracker.trackEvent(
            category = DiagnosticCategory.FACE_VERIFICATION,
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
            category = DiagnosticCategory.FACE_VERIFICATION,
            event = eventType.code,
            description = eventType.description,
            throwable = throwable,
            extras = extras,
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

}
