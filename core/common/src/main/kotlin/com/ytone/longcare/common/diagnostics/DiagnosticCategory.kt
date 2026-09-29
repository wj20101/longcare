package com.ytone.longcare.common.diagnostics

/** Stable business categories, independent of Bugly crash types and scene tags. */
enum class DiagnosticCategory(val code: String) {
    CAMERA("camera"),
    LOCATION("location"),
    COUNTDOWN("countdown"),
    FACE_VERIFICATION("face_verification"),
    PHOTO_UPLOAD("photo_upload"),
    IDENTIFICATION_PHOTO("identification_photo"),
    FACE_CAPTURE("face_capture"),
    ORDER_DETAIL("order_detail"),
    END_SERVICE("end_service"),
    APP_UPDATE("app_update"),
    NFC_WORKFLOW("nfc_workflow"),
}
