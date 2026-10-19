package com.ytone.longcare.features.home.api

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import kotlinx.coroutines.flow.StateFlow
import java.net.URI

private const val DEVICE_PAGE_SCHEME = "https"

data class HomeActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit,
    val onNavigateToHaveServiceUserList: () -> Unit,
    val onNavigateToNoServiceUserList: () -> Unit,
    val onOpenEvaluationReport: (url: String, title: String) -> Unit,
    val onOpenUserAgreement: () -> Unit,
    val onOpenPrivacyPolicy: () -> Unit,
    val onNavigateToCamera: (WatermarkData) -> Unit,
    val capturedImageUriFlow: StateFlow<String?>,
    val clearCapturedImageUri: () -> Unit,
    val onOpenEvaluationPage: (url: String, title: String) -> Unit = onOpenEvaluationReport,
    val onOpenDevicePage: (url: String, title: String) -> Unit = { _, _ -> },
)

/** Device H5 pages follow the application's HTTPS-only network policy. */
fun isHomeDevicePageUrl(url: String): Boolean = try {
    val uri = URI.create(url)
    uri.scheme.equals(DEVICE_PAGE_SCHEME, ignoreCase = true) &&
        !uri.host.isNullOrBlank()
} catch (_: IllegalArgumentException) {
    false
}
