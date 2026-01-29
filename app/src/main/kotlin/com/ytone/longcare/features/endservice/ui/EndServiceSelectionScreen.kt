package com.ytone.longcare.features.endservice.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.R
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.features.endservice.vm.EndServiceSelectionUiState
import com.ytone.longcare.features.endservice.vm.EndServiceSelectionViewModel
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.navigateToNfcSignInForEndOrder
import com.ytone.longcare.theme.bgGradientBrush
import androidx.compose.ui.platform.LocalContext
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.di.ServiceCountdownEntryPoint
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EndServiceSelectionScreen(
    navController: NavController,
    orderInfoRequest: OrderInfoRequestModel,
    initialProjectIdList: List<Int>,
    endType: Int,
    viewModel: EndServiceSelectionViewModel = hiltViewModel(),
    countdownViewModel: ServiceCountdownViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val projectList by viewModel.projectList.collectAsStateWithLifecycle()
    val selectedProjectIds by viewModel.selectedProjectIds.collectAsStateWithLifecycle()
    
    val context = LocalContext.current

    // 获取ServiceCountdownEntryPoint以访问CountdownNotificationManager
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext, ServiceCountdownEntryPoint::class.java
        )
    }
    val countdownNotificationManager = entryPoint.countdownNotificationManager()

    LaunchedEffect(Unit) {
        viewModel.initData(orderInfoRequest, initialProjectIdList)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("确认服务项目", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
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
        }, containerColor = Color.Transparent, modifier = Modifier.background(bgGradientBrush)
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val currentState = uiState) {
                is EndServiceSelectionUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is EndServiceSelectionUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = currentState.message,
                            color = Color.White,
                            fontSize = 16.sp
                        )
                    }
                }
                is EndServiceSelectionUiState.Success -> {
                    // 计算总工时
                    val totalDuration = projectList.filter { selectedProjectIds.contains(it.projectId) }
                        .sumOf { it.serviceTime }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 120.dp), // 为底部按钮留出空间
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(24.dp))

                        // 总工时显示
                        TotalDurationDisplay(totalDuration = totalDuration)

                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Text(
                            text = "请确认本次实际完成的服务项目",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            modifier = Modifier.align(Alignment.Start).padding(bottom = 12.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(projectList) { project ->
                                ServiceSelectionItem(
                                    name = project.projectName,
                                    duration = project.serviceTime,
                                    isSelected = selectedProjectIds.contains(project.projectId),
                                    onClick = { viewModel.toggleSelection(project.projectId) }
                                )
                            }
                        }
                    }
                    
                    // 底部操作区域
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color(0xFFF6F9FF).copy(alpha = 0.9f),
                                        Color(0xFFF6F9FF)
                                    ), startY = 0f, endY = 100f
                                )
                            )
                            .padding(horizontal = 20.dp, vertical = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 全选按钮
                            val isAllSelected = projectList.isNotEmpty() && selectedProjectIds.size == projectList.size
                            SelectAllButton(
                                isAllSelected = isAllSelected,
                                enabled = true,
                                onClick = {
                                    if (isAllSelected) {
                                        viewModel.deselectAll()
                                    } else {
                                        viewModel.selectAll()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                            
                            Spacer(modifier = Modifier.width(8.dp))

                            // 确认结束按钮
                            NextStepButton(
                                text = "确认结束服务",
                                enabled = selectedProjectIds.isNotEmpty(),
                                onClick = {
                                    if (selectedProjectIds.isEmpty()) {
                                        Toast.makeText(context, "请至少选择一个服务项目", Toast.LENGTH_SHORT).show()
                                        return@NextStepButton
                                    }

                                    // --- 先加载已上传的图片数据（在清理前获取） ---
                                    countdownViewModel.loadUploadedImagesFromLocal(orderInfoRequest)
                                    val uploadedImages = countdownViewModel.getCurrentUploadedImages()

                                    // --- 执行资源清理逻辑 ---
                                    CountdownForegroundService.stopCountdown(context)
                                    countdownNotificationManager.cancelCountdownAlarmForOrder(orderInfoRequest.orderId)
                                    AlarmRingtoneService.stopRingtone(context)
                                    countdownViewModel.endService(orderInfoRequest, context)

                                    val beginImgList = uploadedImages[ImageTaskType.BEFORE_CARE]?.mapNotNull { it.key } ?: emptyList()
                                    val centerImgList = uploadedImages[ImageTaskType.CENTER_CARE]?.mapNotNull { it.key } ?: emptyList()
                                    val endImgList = uploadedImages[ImageTaskType.AFTER_CARE]?.mapNotNull { it.key } ?: emptyList()

                                    navController.navigateToNfcSignInForEndOrder(
                                        orderInfoRequest = orderInfoRequest,
                                        params = EndOderInfo(
                                            projectIdList = viewModel.getConfirmedProjectIds(),
                                            beginImgList = beginImgList,
                                            centerImgList = centerImgList,
                                            endImgList = endImgList,
                                            endType = endType
                                        )
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- 复用自 SelectServiceScreen 的组件 ---

@Composable
fun TotalDurationDisplay(totalDuration: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "${totalDuration}分钟",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "本次服务总工时", fontSize = 14.sp, color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun ServiceSelectionItem(
    name: String,
    duration: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = name,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${duration}分钟",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isSelected) stringResource(R.string.common_selected) else stringResource(R.string.common_unselected),
                tint = if (isSelected) Color(0xFF34C759) else Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SelectAllButton(
    isAllSelected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        enabled = enabled,
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary, containerColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C85FE))
    ) {
        if (isAllSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "All selected",
                tint = Color(0xFF34C759),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = "全选", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2C85FE)
        )
    }
}

@Composable
fun NextStepButton(
    text: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier
) {
    val buttonGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
        colors = listOf(Color(0xFF5CA0FF), Color(0xFF2A8CFF))
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(50.dp)
            .background(brush = buttonGradient, shape = androidx.compose.foundation.shape.CircleShape),
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor = Color.White,
            disabledContainerColor = Color.Gray,
            disabledContentColor = Color.White.copy(alpha = 0.7f)
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ServiceProjectItem(
    name: String,
    time: Int,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(0xFF4A90E2),
                    uncheckedColor = Color.Gray
                )
            )
            
            Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${time}分钟",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
