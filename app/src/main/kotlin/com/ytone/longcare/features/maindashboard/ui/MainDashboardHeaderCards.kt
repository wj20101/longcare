package com.ytone.longcare.features.maindashboard.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import com.ytone.longcare.R
import com.ytone.longcare.features.home.api.isHomeDevicePageUrl
import com.ytone.longcare.features.maindashboard.api.MainDashboardActions
import com.ytone.longcare.model.User
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.userIdentityDisplay
import com.ytone.longcare.platform.text.labelRes
import com.ytone.longcare.ui.components.UserAvatar

internal object HomeDeviceTestTags {
    const val CARD = "home_device_status_card"
    const val STATUS = "home_device_status"
    const val INSTALLATION = "home_device_installation"
    const val PREVIEW = "home_device_preview"
}

private const val DARK_SURFACE_LUMINANCE_THRESHOLD = 0.5f

internal fun topHeaderUserBlockWidth(maxWidth: Dp): Dp =
    if (maxWidth >= 480.dp) 160.dp else 120.dp

@Composable
fun TopHeader(user: User, companyName: String) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val useCompactLargeTextLayout =
            maxWidth < 340.dp && LocalDensity.current.fontScale >= 1.3f
        val regularUserBlockWidth = topHeaderUserBlockWidth(maxWidth)
        if (useCompactLargeTextLayout) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderLogo()
                    Spacer(modifier = Modifier.weight(1f))
                    HeaderUserInfo(
                        user = user,
                        modifier = Modifier.widthIn(min = 72.dp, max = 140.dp),
                        userNameMaxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    HeaderAvatar(user)
                }
                HeaderCompanyName(
                    companyName = companyName,
                    maxLines = 2,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    HeaderLogo()
                    HeaderCompanyName(
                        companyName = companyName,
                        maxLines = 3,
                    )
                }
                HeaderUserInfo(
                    user = user,
                    modifier = Modifier.width(regularUserBlockWidth),
                    userNameMaxLines = 1,
                )
                HeaderAvatar(user)
            }
        }
    }
}

@Composable
private fun HeaderLogo() {
    ImageWithAdaptiveWidth(
        drawableResId = R.drawable.app_logo_small_white,
        fixedHeight = 34.dp,
        contentDescription = stringResource(R.string.main_dashboard_logo),
    )
}

@Composable
private fun HeaderCompanyName(
    companyName: String,
    maxLines: Int,
) {
    if (companyName.isNotEmpty()) {
        Text(
            text = companyName,
            color = Color.White,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("home_top_company_name")
                    .padding(top = 4.dp),
        )
    }
}

@Composable
private fun HeaderUserInfo(
    user: User,
    modifier: Modifier,
    userNameMaxLines: Int,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            text = user.userName,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            lineHeight = 20.sp,
            maxLines = userNameMaxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_name"),
        )
        Text(
            text = stringResource(user.userIdentityDisplay().labelRes()),
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("home_top_user_identity"),
        )
    }
}

@Composable
private fun HeaderAvatar(user: User) {
    UserAvatar(
        modifier = Modifier.testTag("home_top_avatar"),
        avatarUrl = user.headUrl,
    )
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
                title = stringResource(R.string.dashboard_pending_care_plans),
                subtitle = if (pendingCarePlanCount > 0) {
                    stringResource(R.string.dashboard_pending_count, pendingCarePlanCount)
                } else {
                    ""
                },
                badgeCount = pendingCarePlanCount,
                onClick = actions.onNavigateToCarePlansList
            )
            InfoCard(
                modifier = Modifier
                    .weight(1f)
                    .testTag("dashboard_records_card"),
                iconRes = R.drawable.main_ic_records,
                title = stringResource(R.string.dashboard_service_records),
                subtitle = stringResource(R.string.dashboard_service_records_description),
                onClick = actions.onNavigateToServiceRecordsList
            )
        }
    }
}

internal fun homeDeviceStatusColor(value: String, fallback: Color): Color {
    val normalized = value.trim()
    if (normalized.isEmpty()) return fallback
    return try {
        Color(normalized.toColorInt())
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

@Composable
internal fun HomeDeviceStatusCard(
    deviceStatus: ZhiChiDeviceStatusModel,
    onOpenPage: (url: String, title: String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val darkSurface = colors.surface.luminance() < DARK_SURFACE_LUMINANCE_THRESHOLD
    val background = if (darkSurface) colors.surface else colorResource(R.color.home_device_surface_light)
    val actionColor = colorResource(if (darkSurface) R.color.home_device_action_dark else R.color.home_device_action_light)
    val fallback = colors.onSurfaceVariant
    val statusColor = remember(deviceStatus.statusColor, fallback) {
        homeDeviceStatusColor(deviceStatus.statusColor, fallback)
    }
    val installTitle = stringResource(R.string.home_device_installation)
    val previewTitle = stringResource(R.string.home_device_preview)

    Surface(
        modifier = Modifier.fillMaxWidth().testTag(HomeDeviceTestTags.CARD),
        shape = RoundedCornerShape(12.dp),
        color = background,
    ) {
        // Keep the default design on one line; large text/long server status can wrap without clipping.
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensionResource(R.dimen.home_device_content_inset)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.home_device_name_spacing)),
                verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.home_ic_zhichi_device),
                    contentDescription = null,
                    modifier = Modifier.size(dimensionResource(R.dimen.home_device_icon_size)).clip(CircleShape),
                )
                Text(stringResource(R.string.home_device_name), fontSize = 14.sp, color = colors.onSurface)
            }
            Text(
                deviceStatus.status,
                color = statusColor,
                fontSize = 14.sp,
                modifier = Modifier.testTag(HomeDeviceTestTags.STATUS),
            )
            HomeDevicePageButton(installTitle, deviceStatus.installUrl, HomeDeviceTestTags.INSTALLATION, actionColor, onOpenPage)
            if (deviceStatus.deviceSn.isNotBlank()) {
                HomeDevicePageButton(previewTitle, deviceStatus.previewUrl, HomeDeviceTestTags.PREVIEW, actionColor, onOpenPage)
            }
        }
    }
}

@Composable
private fun HomeDevicePageButton(
    title: String,
    url: String,
    tag: String,
    color: Color,
    onOpenPage: (url: String, title: String) -> Unit,
) {
    TextButton(
        onClick = { onOpenPage(url, title) },
        enabled = isHomeDevicePageUrl(url),
        modifier = Modifier.heightIn(min = dimensionResource(R.dimen.home_device_min_touch_target)).testTag(tag),
        shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(contentColor = color),
        contentPadding = PaddingValues(0.dp),
    ) {
        // Reserve each button's own touch area; automatic expansion alone can overlap adjacent FlowRow items.
        Row(
            modifier = Modifier.border(BorderStroke(1.dp, LocalContentColor.current), CircleShape)
                .padding(start = 9.dp, end = 5.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 14.sp, lineHeight = 18.sp)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}
