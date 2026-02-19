package com.ytone.longcare.features.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ytone.longcare.features.home.api.HomeActions
import com.ytone.longcare.theme.LongCareTheme

@Preview
@Composable
fun HomeScreenPreview() {
    LongCareTheme {
        HomeScreen(
            actions = HomeActions(
                onNavigateToCarePlansList = {},
                onNavigateToServiceRecordsList = {},
                onNavigateToNursingExecution = { _ -> },
                onNavigateToService = { _ -> },
                onNavigateToServiceCountdown = { _, _ -> },
                onNavigateToHaveServiceUserList = {},
                onNavigateToNoServiceUserList = {}
            )
        )
    }
}
