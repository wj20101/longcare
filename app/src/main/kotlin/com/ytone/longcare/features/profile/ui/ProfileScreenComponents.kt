package com.ytone.longcare.features.profile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.features.profile.api.ProfileActions
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.User
import com.ytone.longcare.model.userIdentityShow
import com.ytone.longcare.ui.components.UserAvatar

@Composable
fun UserInfoSection(user: User) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(avatarUrl = user.headUrl)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = user.userName,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color.White
            )
            Text(
                text = user.userIdentityShow(),
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp,
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

@Composable
fun StatsCard(actions: ProfileActions, stats: NurseServiceTimeModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatItem(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                value = stats.haveServiceTime.toString(),
                label = "已服务工时",
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
                label = "服务次数"
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
                label = "未服务工时",
                onClick = actions.onNavigateToNoServiceUserList
            )
        }
    }
}

@Composable
fun StatItem(
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
            color = if (onClick != null) Color(0xFF333333) else Color(0xFF666666)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (onClick != null) Color(0xFF666666) else Color.Gray,
            fontSize = 12.sp
        )
    }
}
