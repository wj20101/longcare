package com.ytone.longcare.features.home.api

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import kotlinx.coroutines.flow.StateFlow

data class HomeActions(
    val onNavigateToCarePlansList: () -> Unit,
    val onNavigateToServiceRecordsList: () -> Unit,
    val onNavigateToNursingExecution: (OrderKey) -> Unit,
    val onNavigateToService: (OrderKey) -> Unit,
    val onNavigateToServiceCountdown: (OrderKey, List<Int>) -> Unit,
    val onNavigateToHaveServiceUserList: () -> Unit,
    val onNavigateToNoServiceUserList: () -> Unit,
    val onOpenWebPage: (url: String, title: String) -> Unit,
    val onOpenUserAgreement: () -> Unit,
    val onOpenPrivacyPolicy: () -> Unit,
    val onNavigateToCamera: (WatermarkData) -> Unit,
    val capturedImageUriFlow: StateFlow<String?>,
    val clearCapturedImageUri: () -> Unit,
)
