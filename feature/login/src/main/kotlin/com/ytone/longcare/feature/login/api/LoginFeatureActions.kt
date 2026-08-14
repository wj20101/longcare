package com.ytone.longcare.feature.login.api

/**
 * Login 特性的导航/副作用动作契约。
 * 用于在 UI 层移除对 NavController 的直接依赖。
 */
data class LoginFeatureActions(
    val onLoginSuccess: () -> Unit,
    val onOpenWebPage: (url: String, title: String) -> Unit,
    val validationEntryActions: LoginValidationEntryActions = LoginValidationEntryActions(),
)

/**
 * 登录页隐藏功能验证入口的导航动作。
 * 契约保留在 login feature 中，使登录 UI 不直接依赖 NavController。
 */
data class LoginValidationEntryActions(
    val onOpenCameraValidation: () -> Unit = {},
    val onOpenBackupFaceVerification: () -> Unit = {},
    val onOpenManualFaceCapture: () -> Unit = {},
)
