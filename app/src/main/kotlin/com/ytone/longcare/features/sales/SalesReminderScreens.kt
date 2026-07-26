package com.ytone.longcare.features.sales

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.model.UserLatentListModel

@Composable
internal fun SalesReminderListScreen(
    customers: List<UserLatentListModel>,
    onBack: () -> Unit,
    onReminderClick: (Int) -> Unit,
) {
    val reminders = customers.filter { it.checkState == 0 }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "代办提醒",
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            if (reminders.isEmpty()) {
                item {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .salesWhiteCard()
                                .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "暂无待办提醒",
                            color = SalesTextPrimary,
                            fontSize = 17.sp,
                        )
                        Text(
                            text = "未完成评估的客户会显示在这里",
                            color = SalesTextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            } else {
                items(
                    items = reminders,
                    key = UserLatentListModel::id,
                ) { customer ->
                    SalesReminderCard(
                        customer = customer,
                        onClick = { onReminderClick(customer.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SalesReminderCard(
    customer: UserLatentListModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(76.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "去${customer.userName.ifBlank { "客户" }}家完成设备安装",
                color = SalesTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "提醒时间待安排",
                color = SalesTextSecondary,
                fontSize = 14.sp,
            )
        }
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowRight,
            contentDescription = "查看待办详情",
            tint = Color(0xFFB7C8DC),
        )
    }
}

@Composable
internal fun SalesReminderDetailScreen(
    customer: UserLatentListModel?,
    onBack: () -> Unit,
    onOpenCustomer: (Int) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "代办提醒详情",
            onBack = onBack,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .salesWhiteCard()
                        .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Text(
                    text =
                        "去${customer?.userName?.ifBlank { "客户" } ?: "客户"}家完成设备安装",
                    modifier = Modifier.fillMaxWidth(),
                    color = SalesTextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "事情详情：",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text =
                        buildString {
                            append("请与客户确认上门时间，完成评估设备连接和身体能力评估。")
                            customer?.liveAddress
                                ?.takeIf { it.isNotBlank() }
                                ?.let { append("\n客户地址：$it") }
                        },
                    color = SalesTextSecondary,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                )
                Spacer(Modifier.height(26.dp))
                Text(
                    text = "提醒时间：",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "待安排",
                    color = SalesTextSecondary,
                    fontSize = 16.sp,
                )
            }
            SalesOutlinedActionButton(
                text = "返回",
                onClick = onBack,
            )
            if (customer != null) {
                SalesPrimaryButton(
                    text = "查看客户",
                    onClick = { onOpenCustomer(customer.id) },
                )
            }
        }
    }
}
