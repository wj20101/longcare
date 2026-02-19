package com.ytone.longcare.features.nursing.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.isServiceRecordState
import com.ytone.longcare.model.toStateDisplayText

@Composable
fun PlanList(
    plans: List<ServiceOrderModel>,
    isLoading: Boolean,
    onGoToDetailClick: (ServiceOrderModel) -> Unit
) {
    val modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        .background(Color.White)
    if (isLoading) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (plans.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "暂无服务订单",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "当前日期没有安排服务订单",
                    fontSize = 14.sp,
                    color = Color.LightGray
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(plans) { index, plan ->
                    OrderListItem(
                        modifier = Modifier.clickable { onGoToDetailClick.invoke(plan) },
                        item = plan
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = 1.dp,
                        color = Color(0xFFF0F0F0)
                    )
                    if (index == plans.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun OrderListItem(modifier: Modifier = Modifier, item: ServiceOrderModel) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = stringResource(id = R.string.service_order_work_hours, item.planTotalTime),
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "地址: ${item.liveAddress}",
                color = Color.Gray,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }

        Text(
            text = item.state.toStateDisplayText(),
            color = if (item.state.isServiceRecordState()) Color.Green else Color.Red,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.common_details),
            tint = Color.LightGray
        )
    }
}
