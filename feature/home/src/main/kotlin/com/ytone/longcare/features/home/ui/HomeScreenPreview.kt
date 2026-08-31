package com.ytone.longcare.features.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.features.home.api.HomeFeatureConfig
import com.ytone.longcare.features.home.api.HomeOrderStateSource
import com.ytone.longcare.core.ui.message.UiMessage
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.UserScopeKey
import kotlinx.coroutines.flow.StateFlow
import com.ytone.longcare.theme.LongCareTheme
import kotlinx.coroutines.flow.MutableStateFlow

@Preview
@Composable
internal fun HomeScreenPreview() {
    LongCareTheme {
        HomeCareContent(
            actions = previewActions(),
            config = HomeFeatureConfig(versionName = "1.0", versionCode = 1),
            user = CurrentUser(
                scopeKey = UserScopeKey(1, 1, 1),
                userName = "护理员",
                headUrl = "",
                userIdentity = 1,
                gender = 0,
            ),
            selectedDashboardTab = 0,
            onSelectedDashboardTab = {},
            orderStateSource = PreviewOrderStateSource,
        )
    }
}

private fun previewActions() = HomeActions(
                onNavigateToCarePlansList = {},
                onNavigateToServiceRecordsList = {},
                onNavigateToNursingExecution = { _ -> },
                onNavigateToService = { _ -> },
                onNavigateToServiceCountdown = { _, _ -> },
                onNavigateToHaveServiceUserList = {},
                onNavigateToNoServiceUserList = {},
                onOpenWebPage = { _, _ -> },
                onOpenUserAgreement = {},
                onOpenPrivacyPolicy = {},
                onNavigateToCamera = {},
                capturedImageUriFlow = MutableStateFlow(null),
                clearCapturedImageUri = {},
)

private object PreviewOrderStateSource : HomeOrderStateSource {
    override val todayOrders: StateFlow<List<TodayServiceOrderModel>> = MutableStateFlow(emptyList())
    override val inProgressOrders: StateFlow<List<ServiceOrderModel>> = MutableStateFlow(emptyList())
    override val messages: StateFlow<List<UiMessage>> = MutableStateFlow(emptyList())
    override fun refreshTodayOrders() = Unit
    override fun refreshInProgressOrders() = Unit
    override fun consumeMessage(id: Long) = Unit
}
