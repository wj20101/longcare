package com.ytone.longcare.features.sales

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.ui.TopHeader
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserLatentCheckState
import com.ytone.longcare.model.UserLatentListModel

@Composable
internal fun SalesDashboardScreen(
    user: User,
    companyName: String,
    customers: List<UserLatentListModel>,
    toDoCount: Int?,
    isToDoCountLoading: Boolean,
    onRegisterCustomer: () -> Unit,
    onReminders: () -> Unit,
    onCustomerClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            TopHeader(
                user = user,
                companyName = companyName,
            )
        }
        item {
            Image(
                painter = painterResource(R.drawable.sales_home_banner),
                contentDescription = "居家养老方案",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2712f / 960f)
                        .clip(RoundedCornerShape(15.dp)),
                contentScale = ContentScale.FillBounds,
            )
        }
        item {
            if (useSalesLargeTextLayout()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_registration,
                        title = "对象登记",
                        subtitle = "客访登记信息",
                        onClick = onRegisterCustomer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_signin,
                        title = "待办提醒",
                        subtitle =
                            toDoCount.toSalesToDoSubtitle(isToDoCountLoading),
                        onClick = onReminders,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_registration,
                        title = "对象登记",
                        subtitle = "客访登记信息",
                        onClick = onRegisterCustomer,
                        modifier = Modifier.weight(1f),
                    )
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_signin,
                        title = "待办提醒",
                        subtitle =
                            toDoCount.toSalesToDoSubtitle(isToDoCountLoading),
                        onClick = onReminders,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Text(
                text = "最新客户",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp),
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (customers.isEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 82.dp)
                            .salesWhiteCard()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "暂无客户",
                        color = SalesTextPrimary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = "登记客户后会显示在这里",
                        color = SalesTextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            items(
                items = customers.take(4),
                key = UserLatentListModel::id,
            ) { customer ->
                SalesLatestCustomerCard(
                    customer = customer,
                    onClick = { onCustomerClick(customer.id) },
                )
            }
        }
    }
}

@Composable
private fun SalesHomeFeatureCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 70.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF3F7FE))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SalesFeatureIcon(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SalesTextPrimary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = SalesTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SalesLatestCustomerCard(
    customer: UserLatentListModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = customer.userName.ifBlank { "未命名客户" },
                    modifier = Modifier.weight(1f),
                    color = SalesTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = customer.checkState.toSalesHomeCheckStateLabel(),
                    color = customer.checkState.toSalesHomeCheckStateColor(),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = "地址：${customer.liveAddress.ifBlank { "待补充" }}",
                color = SalesTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = "查看客户",
            tint = Color(0xFFB9C9DD),
        )
    }
}

private fun Int?.toSalesToDoSubtitle(isLoading: Boolean): String =
    when {
        this != null && this > 0 -> "${this}项待处理"
        this == 0 -> "暂无待办事项"
        isLoading -> "正在加载待办"
        else -> "点击查看待办"
    }

private fun Int.toSalesHomeCheckStateLabel(): String =
    when (this) {
        UserLatentCheckState.NOT_SUBMITTED -> "未申报"
        UserLatentCheckState.PENDING_REVIEW -> "待审核"
        UserLatentCheckState.APPROVED -> "审核通过"
        UserLatentCheckState.REJECTED -> "审核不通过"
        else -> "未知状态"
    }

private fun Int.toSalesHomeCheckStateColor(): Color =
    when (this) {
        UserLatentCheckState.NOT_SUBMITTED -> Color(0xFFF09A00)
        UserLatentCheckState.PENDING_REVIEW -> Color(0xFF1688F8)
        UserLatentCheckState.APPROVED -> Color(0xFF20B83D)
        UserLatentCheckState.REJECTED -> Color(0xFFFF4A19)
        else -> SalesTextSecondary
    }
