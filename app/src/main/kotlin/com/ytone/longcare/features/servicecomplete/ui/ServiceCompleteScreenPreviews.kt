package com.ytone.longcare.features.servicecomplete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.theme.bgGradientBrush
import androidx.compose.material3.Text

@Preview
@Composable
fun ServiceCompleteScreenPreview() {
    val serviceSummary = ServiceSummary(
        clientName = "孙连中",
        clientAge = 72,
        clientIdNumber = "310023023020320302",
        clientAddress = "浙江省杭州市西湖区爱家小区32号501",
        serviceContent = "助浴",
        duration = "3小时21分钟"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
        ) {
            item {
                Text(
                    text = "已完成服务，请确认服务内容",
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                ThankYouCard()
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                ServiceChecklistSection(summary = serviceSummary)
            }
        }
    }
}
