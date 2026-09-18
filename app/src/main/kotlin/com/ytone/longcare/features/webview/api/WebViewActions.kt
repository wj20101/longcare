package com.ytone.longcare.features.webview.api

data class WebViewActions(
    val onNavigateBack: () -> Unit,
    val isCurrentPage: () -> Boolean = { true },
    val onCloseFromH5: () -> Unit = onNavigateBack,
)
