package com.ytone.longcare.features.sales

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R

@Composable
internal fun SalesDeviceStatusScreen(
    connectedDeviceName: String?,
    tokenReady: Boolean,
    progressText: String,
    onBack: () -> Unit,
    onStartEvaluation: () -> Unit,
) {
    val isConnected = !connectedDeviceName.isNullOrBlank()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SalesTopBar(
            title = "设备检测",
            onBack = onBack,
        )
        Text(
            text = "当前设备状态",
            color = Color.White,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 5.dp, bottom = 28.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(270.dp)
                    .salesWhiteCard()
                    .clickable(
                        enabled = tokenReady,
                        onClick = onStartEvaluation,
                    )
                    .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.sales_qlz_device_design),
                contentDescription = "健康评估设备",
                modifier =
                    Modifier
                        .width(200.dp)
                        .height(120.dp),
                contentScale = ContentScale.Fit,
            )
            SalesStatusBadge(
                connected = isConnected,
                label =
                    if (isConnected) {
                        "设备已连接"
                    } else {
                        "设备未连接"
                    },
            )
            if (isConnected) {
                Text(
                    text = connectedDeviceName.orEmpty(),
                    color = SalesTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(26.dp))
        SalesPrimaryButton(
            text =
                when {
                    progressText.isNotBlank() -> progressText
                    isConnected -> "开始评估"
                    else -> "连接设备并开始评估"
                },
            onClick = onStartEvaluation,
            modifier = Modifier.padding(horizontal = 18.dp),
            enabled = tokenReady,
        )
        Text(
            text =
                if (tokenReady) {
                    "进入评估页面后，请按页面提示连接设备"
                } else {
                    "正在准备评估…"
                },
            color = Color.White.copy(alpha = 0.82f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }
}

@Composable
internal fun SalesEvaluationGuideScreen(
    connectedDeviceName: String?,
    progressText: String,
    onBack: () -> Unit,
    onOpenSdk: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SalesTopBar(
            title = "评估",
            onBack = onBack,
        )
        Text(
            text = "设备已连接成功请按照图示进行评估检测",
            color = Color.White,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 26.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
                    .height(340.dp)
                    .salesWhiteCard()
                    .clickable(onClick = onOpenSdk)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Image(
                    painter = painterResource(R.drawable.sales_qlz_device_design),
                    contentDescription = null,
                    modifier = Modifier.size(92.dp),
                    contentScale = ContentScale.Fit,
                )
                SalesStatusBadge(
                    connected = true,
                    label = "设备已连接",
                )
            }
            Image(
                painter = painterResource(R.drawable.sales_evaluation_instruction),
                contentDescription = "握持设备评估示意图",
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                contentScale = ContentScale.Fit,
            )
            Text(
                text = "请将每根手指触碰到相应的金属片",
                color = Color(0xFF282828),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(22.dp))
        SalesPrimaryButton(
            text = progressText.ifBlank { "进入评估页面开始检测" },
            onClick = onOpenSdk,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        if (!connectedDeviceName.isNullOrBlank()) {
            Text(
                text = "当前设备：$connectedDeviceName",
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
internal fun SalesEvaluationCompleteScreen(
    hasReport: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenReport: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SalesTopBar(
            title = "评估完成",
            onBack = onBack,
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            SalesSuccessPanel(title = "评估成功")
            SalesOutlinedActionButton(
                text = "完成",
                onClick = onDone,
            )
            if (hasReport) {
                SalesPrimaryButton(
                    text = "查看评估报告",
                    onClick = onOpenReport,
                )
            }
        }
    }
}
