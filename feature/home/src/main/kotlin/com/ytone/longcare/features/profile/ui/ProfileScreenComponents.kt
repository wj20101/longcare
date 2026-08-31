package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.userIdentityDisplay
import com.ytone.longcare.core.ui.text.labelRes
import com.ytone.longcare.ui.components.UserAvatar
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.feature.home.R
import com.ytone.longcare.core.ui.R as CoreUiR

private val StatsCardRowMinHeight = 88.dp

@Composable
internal fun UserInfoSection(user: CurrentUser) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(
            modifier = Modifier.testTag("profile_user_avatar"),
            avatarUrl = user.headUrl,
            size = 40.dp
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.userName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_user_name")
            )
            Text(
                text = stringResource(user.userIdentityDisplay().labelRes()),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
                lineHeight = 12.sp,
                style = TextStyle(
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Center,
                        trim = LineHeightStyle.Trim.Both
                    )
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("profile_user_identity")
            )
        }
    }
}

@Composable
internal fun StatsCard(actions: ProfileActions, stats: NurseServiceTimeModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StatsCardRowMinHeight)
                .testTag("profile_stats_card_row"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.haveServiceTime.toString(),
                label = stringResource(CoreUiR.string.profile_stat_served_hours),
                onClick = actions.onNavigateToHaveServiceUserList
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.haveServiceNum.toString(),
                label = stringResource(R.string.profile_stat_service_count)
            )
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 16.dp),
                thickness = 1.dp,
                color = Color(0xFFF0F0F0)
            )
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.noServiceTime.toString(),
                label = stringResource(CoreUiR.string.profile_stat_unserved_hours),
                onClick = actions.onNavigateToNoServiceUserList
            )
        }
    }
}

@Composable
internal fun StatItem(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable { onClick() }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    ) {
        Text(
            text = value,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = if (onClick != null) Color(0xFF333333) else Color(0xFF666666),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (onClick != null) Color(0xFF666666) else Color.Gray,
            fontSize = 12.sp,
            lineHeight = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
