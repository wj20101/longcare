package com.ytone.longcare.features.userservicerecord.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.ytone.longcare.model.UserOrderModel
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory

@Composable
fun UserServiceRecordContent(
    serviceRecords: List<UserOrderModel>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        if (serviceRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "暂无服务记录", color = Color.Gray, fontSize = 16.sp)
            }
        } else {
            Box {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.9f))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(serviceRecords) { index, record ->
                            ServiceRecordItem(
                                record = record,
                                index = index,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (index < serviceRecords.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    thickness = 0.5.dp,
                                    color = Color.Gray.copy(alpha = 0.3f)
                                )
                            }
                        }
                    }
                }

                ServiceHoursTag(
                    modifier = Modifier.align(Alignment.TopStart),
                    tagText = "已服务工时",
                    tagCategory = TagCategory.DEFAULT
                )
            }
        }
    }
}

@Composable
fun ServiceRecordItem(
    record: UserOrderModel,
    index: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = "服务记录${index + 1}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = stringResource(
                id = R.string.service_order_work_hours_total,
                record.totalServiceTime
            ),
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                id = R.string.service_order_work_hours_true,
                record.trueServiceTime
            ),
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "服务时间：${record.startTime} - ${record.endTime}",
            fontSize = 14.sp,
            color = Color.Gray
        )
    }
}
