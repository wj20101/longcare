package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.core.ui.R as CoreUiR

@Composable
internal fun HomeBannerCard() {
    val sampleBanners = listOf(BannerItem(1, R.drawable.main_banner, "Banner 1"))
    ImageBannerPager(
        bannerItems = sampleBanners,
        modifier = Modifier.height(120.dp),
    )
}

@Composable
internal fun DashboardGridWithImages(
    pendingCarePlanCount: Int,
    actions: MainDashboardActions,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InfoCard(
                modifier = Modifier.weight(1f).testTag("dashboard_pending_card"),
                iconRes = R.drawable.main_ic_plan,
                title = stringResource(CoreUiR.string.dashboard_pending_care_plans),
                subtitle = if (pendingCarePlanCount > 0) {
                    stringResource(R.string.dashboard_pending_count, pendingCarePlanCount)
                } else {
                    ""
                },
                badgeCount = pendingCarePlanCount,
                onClick = actions.onNavigateToCarePlansList,
            )
            InfoCard(
                modifier = Modifier.weight(1f).testTag("dashboard_records_card"),
                iconRes = R.drawable.main_ic_records,
                title = stringResource(CoreUiR.string.dashboard_service_records),
                subtitle = stringResource(R.string.dashboard_service_records_description),
                onClick = actions.onNavigateToServiceRecordsList,
            )
        }
    }
}
