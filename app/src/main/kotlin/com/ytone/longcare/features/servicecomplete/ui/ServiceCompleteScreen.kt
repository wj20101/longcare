package com.ytone.longcare.features.servicecomplete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.shared.vm.OrderDetailViewModel
import com.ytone.longcare.features.servicecomplete.api.ServiceCompleteActions
import com.ytone.longcare.navigation.ServiceCompleteData
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.ui.components.BottomSafeActionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCompleteScreen(
    actions: ServiceCompleteActions,
    orderKey: OrderKey,
    serviceCompleteData: ServiceCompleteData,
    viewModel: OrderDetailViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    // 统一处理系统返回键，与导航按钮行为一致（返回首页并清空堆栈）
    CustomBackHandler(customAction = actions.onNavigateHomeAndClearStack)
    
    // 直接使用传入的数据创建 ServiceSummary
    val serviceSummary = ServiceSummary(
        clientName = serviceCompleteData.clientName,
        clientAge = serviceCompleteData.clientAge,
        clientIdNumber = serviceCompleteData.clientIdNumber,
        clientAddress = serviceCompleteData.clientAddress,
        serviceContent = serviceCompleteData.serviceContent,
        duration = formatServiceDuration(context, serviceCompleteData.trueServiceTime)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.service_complete_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        // 清除选中项目数据后再返回
                        viewModel.clearSelectedProjects(orderKey.orderId)
                        actions.onNavigateHomeAndClearStack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }, containerColor = Color.Transparent, bottomBar = {
            Surface(modifier = Modifier.fillMaxWidth()) {
                BottomSafeActionContainer(
                    horizontalPadding = 24.dp,
                    topPadding = 16.dp,
                    extraBottomPadding = 16.dp
                ) {
                    Box {
                        ActionButton(text = stringResource(R.string.common_complete), onClick = {
                            // 清除选中项目数据后再返回首页
                            viewModel.clearSelectedProjects(orderKey.orderId)
                            actions.onNavigateHomeAndClearStack()
                        })
                    }
                }
            }
        }) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues), // 应用来自Scaffold的padding (包括了底部按钮的空间)
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.service_complete_confirm_content),
                        color = Color.White,
                        fontSize = 14.sp,
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
}
