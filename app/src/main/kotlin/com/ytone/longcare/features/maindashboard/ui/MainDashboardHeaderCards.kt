package com.ytone.longcare.features.maindashboard.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.model.User
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.ui.components.UserAvatar

@Composable
fun TopHeader(user: User, companyName: String) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
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
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = user.userName,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = user.userIdentityShow(),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))

            UserAvatar(avatarUrl = user.headUrl)
        }
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.main_ic_plan,
                title = "待护理计划",
                subtitle = if (pendingCarePlanCount > 0) "你有${pendingCarePlanCount}个护理待执行" else "",
                badgeCount = pendingCarePlanCount,
                onClick = actions.onNavigateToCarePlansList
            )
            InfoCard(
                modifier = Modifier.weight(1f),
                iconRes = R.drawable.main_ic_records,
                title = "已服务记录",
                subtitle = "查看过往服务记录",
                onClick = actions.onNavigateToServiceRecordsList
            )
        }
    }
}

@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    badgeCount: Int? = null,
    iconContentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        onClick = { onClick?.invoke() },
        modifier = modifier.height(65.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = iconContentDescription,
                modifier = Modifier.size(32.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badgeCount != null && badgeCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(containerColor = Color(0xFFFFC107)) {
                            Text(
                                text = badgeCount.toString(),
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        lineHeight = 12.sp,
                        style = TextStyle(
                            lineHeightStyle = LineHeightStyle(
                                alignment = LineHeightStyle.Alignment.Center,
                                trim = LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ImageWithAdaptiveWidth(
    @DrawableRes drawableResId: Int,
    fixedHeight: Dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    val painter = painterResource(id = drawableResId)

    val aspectRatio = if (painter.intrinsicSize.height > 0) {
        painter.intrinsicSize.width / painter.intrinsicSize.height
    } else {
        1.0f
    }

    Image(
        painter = painter,
        contentDescription = contentDescription,
        contentScale = ContentScale.FillHeight,
        modifier = modifier
            .height(fixedHeight)
            .aspectRatio(aspectRatio)
    )
}
