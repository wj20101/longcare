package com.ytone.longcare.feature.login.api

/**
 * Login 特性的导航/副作用动作契约。
 * 用于在 UI 层移除对 NavController 的直接依赖。
 */
data class LoginFeatureActions(
    val onLoginSuccess: () -> Unit,
    val onOpenWebPage: (url: String, title: String) -> Unit,
    val onOpenNfcTest: () -> Unit = {},
    val onOpenCameraTest: () -> Unit = {},
    val onOpenFaceVerificationTest: () -> Unit = {},
    val onOpenManualFaceCapture: () -> Unit = {}
)
