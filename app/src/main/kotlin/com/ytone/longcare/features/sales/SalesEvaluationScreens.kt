package com.ytone.longcare.features.sales

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
    val deviceStatusLabel =
        stringResource(
            if (isConnected) {
                R.string.sales_evaluation_device_connected
            } else {
                R.string.sales_evaluation_device_disconnected
            }
        )
    val actionText =
        when {
            progressText.isNotBlank() -> progressText
            isConnected -> stringResource(R.string.sales_evaluation_start)
            else -> stringResource(R.string.sales_evaluation_connect_and_start)
        }
    val statusHint =
        stringResource(
            if (tokenReady) {
                R.string.sales_evaluation_sdk_connection_hint
            } else {
                R.string.sales_evaluation_preparing
            }
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_evaluation_device_status_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 5.dp,
                    bottom = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.sales_evaluation_device_status),
                    color = Color.White,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 270.dp)
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
                        contentDescription =
                            stringResource(
                                R.string.sales_evaluation_device_image_description
                            ),
                        modifier =
                            Modifier
                                .width(200.dp)
                                .height(120.dp),
                        contentScale = ContentScale.Fit,
                    )
                    SalesStatusBadge(
                        connected = isConnected,
                        label = deviceStatusLabel,
                    )
                    if (isConnected) {
                        Text(
                            text = connectedDeviceName.orEmpty(),
                            color = SalesTextSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item {
                SalesPrimaryButton(
                    text = actionText,
                    onClick = onStartEvaluation,
                    enabled = tokenReady,
                )
            }
            item {
                Text(
                    text = statusHint,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
internal fun SalesEvaluationGuideScreen(
    connectedDeviceName: String?,
    progressText: String,
    onBack: () -> Unit,
    onOpenSdk: () -> Unit,
) {
    val isConnected = !connectedDeviceName.isNullOrBlank()
    val deviceStatusLabel =
        stringResource(
            if (isConnected) {
                R.string.sales_evaluation_device_connected
            } else {
                R.string.sales_evaluation_device_disconnected
            }
        )
    val guideText =
        stringResource(
            if (isConnected) {
                R.string.sales_evaluation_guide_connected
            } else {
                R.string.sales_evaluation_guide_disconnected
            }
        )
    val defaultActionText =
        stringResource(
            if (isConnected) {
                R.string.sales_evaluation_continue
            } else {
                R.string.sales_evaluation_connect_and_start
            }
        )
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SalesTopBar(
            title = stringResource(R.string.sales_evaluation_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = guideText,
                    color = Color.White,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 340.dp)
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
                            connected = isConnected,
                            label = deviceStatusLabel,
                        )
                    }
                    Image(
                        painter = painterResource(R.drawable.sales_evaluation_instruction),
                        contentDescription =
                            stringResource(
                                R.string.sales_evaluation_hold_device_description
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(150.dp),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        text = stringResource(R.string.sales_evaluation_finger_instruction),
                        color = Color(0xFF282828),
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            item {
                SalesPrimaryButton(
                    text = progressText.ifBlank { defaultActionText },
                    onClick = onOpenSdk,
                )
            }
            if (!connectedDeviceName.isNullOrBlank()) {
                item {
                    Text(
                        text =
                            stringResource(
                                R.string.sales_evaluation_current_device,
                                connectedDeviceName,
                            ),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
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
            title = stringResource(R.string.sales_evaluation_complete_title),
            onBack = onBack,
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 18.dp,
                    end = 18.dp,
                    top = 10.dp,
                    bottom = 20.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            item {
                SalesSuccessPanel(
                    title = stringResource(R.string.sales_evaluation_success)
                )
            }
            item {
                SalesOutlinedActionButton(
                    text = stringResource(R.string.sales_common_done),
                    onClick = onDone,
                )
            }
            if (hasReport) {
                item {
                    SalesPrimaryButton(
                        text = stringResource(R.string.sales_customer_view_report),
                        onClick = onOpenReport,
                    )
                }
            }
        }
    }
}
