package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.model.User
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.ui.components.UserAvatar

@Composable
fun TopHeader(user: User, companyName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            ImageWithAdaptiveWidth(
                drawableResId = R.drawable.app_logo_small_white,
                fixedHeight = 34.dp,
                contentDescription = stringResource(R.string.main_dashboard_logo)
            )
            if (companyName.isNotEmpty()) {
                Text(
                    text = companyName,
                    color = Color.White,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_top_company_name")
                        .padding(top = 4.dp)
                )
            }
        }
        Column(
            modifier = Modifier.widthIn(min = 72.dp, max = 120.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = user.userName,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_name")
            )
            Text(
                text = user.userIdentityShow(),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_identity")
            )
        }
        UserAvatar(
            modifier = Modifier.testTag("home_top_avatar"),
            avatarUrl = user.headUrl
        )
    }
}

@Composable
fun HomeBannerCard() {
    val sampleBanners = listOf(
        BannerItem(1, R.drawable.main_banner, "Banner 1")
    )

    ImageBannerPager(
        bannerItems = sampleBanners,
        modifier = Modifier.height(120.dp)
    )
}

@Composable
fun DashboardGridWithImages(
    pendingCarePlanCount: Int,
    actions: MainDashboardActions
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_pending_card"),
                iconRes = R.drawable.main_ic_plan,
                title = "待护理计划",
                subtitle = if (pendingCarePlanCount > 0) "你有${pendingCarePlanCount}个护理待执行" else "",
                badgeCount = pendingCarePlanCount,
                onClick = actions.onNavigateToCarePlansList
            )
            InfoCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_records_card"),
                iconRes = R.drawable.main_ic_records,
                title = "已服务记录",
                subtitle = "查看过往服务记录",
                onClick = actions.onNavigateToServiceRecordsList
            )
        }
    }
}
