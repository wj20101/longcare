package com.ytone.longcare.features.userservicerecord.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.api.response.UserOrderModel
import com.ytone.longcare.features.userservicerecord.vm.UserServiceRecordViewModel
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.theme.bgGradientBrush

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserServiceRecordScreen(
    userId: Long,
    userName: String,
    userAddress: String,
    onBackClick: () -> Unit,
    viewModel: UserServiceRecordViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val serviceRecords by viewModel.serviceRecordListState.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    // 页面初始化时加载数据
    LaunchedEffect(userId) {
        viewModel.getUserServiceRecords(userId)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                userName,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = Color.White
                            )
                            if (userAddress.isNotBlank()) {
                                Text(
                                    "地址: $userAddress",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }, navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }, containerColor = Color.Transparent
        ) { paddingValues ->
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center), color = Color.White
                )
            } else {
                UserServiceRecordContent(
                    serviceRecords = serviceRecords, modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

@Composable
fun UserServiceRecordContent(
    serviceRecords: List<UserOrderModel>, modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // 服务记录列表
        if (serviceRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.9f))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无服务记录", color = Color.Gray, fontSize = 16.sp
                )
            }
        } else {
            // 使用Box实现ServiceHoursTag叠加在ServiceRecordList之上
            Box {
                // 列表内容区域，需要给顶部留出空间给ServiceHoursTag
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp) // 给ServiceHoursTag留出空间
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

                            // 添加分割线，除了最后一项
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

                // ServiceHoursTag 叠加在白色容器的左上角外侧
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
        // 服务记录标题
        Text(
            text = "服务记录${index + 1}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 总的服务时长
        Text(
            text = stringResource(
                id = R.string.service_order_work_hours_total,
                record.totalServiceTime
            ), fontSize = 14.sp, color = Color.Gray
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 实际服务时长
        Text(
            text = stringResource(
                id = R.string.service_order_work_hours_true,
                record.trueServiceTime
            ), fontSize = 14.sp, color = Color.Gray
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 服务时间
        Text(
            text = "服务时间：${record.startTime} - ${record.endTime}",
            fontSize = 14.sp,
            color = Color.Gray
        )

        // 分割线 - 移除错误的逻辑，分割线将在LazyColumn的itemsIndexed中处理
    }
}