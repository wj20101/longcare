package com.ytone.longcare.features.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

/**
 * 带自动签名的人脸验证页面
 * 展示如何使用自动获取签名参数的人脸验证功能
 * 
 * @param onNavigateBack 返回回调
 * @param onVerificationSuccess 验证成功回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationWithAutoSignScreen(
    onNavigateBack: () -> Unit,
    onVerificationSuccess: (WbFaceVerifyResult) -> Unit,
    viewModel: FaceVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    
    // 默认配置（实际使用时应该从配置文件或服务器获取）
    val defaultConfig = remember {
        FaceVerificationManager.TencentCloudConfig(
            appId = BuildConfig.TX_ID,
            secret = BuildConfig.TX_Secret
        )
    }
    val config = defaultConfig
    val currentFaceId = "123456"
    val currentOrderNo = "order_${System.currentTimeMillis()}"
    val currentUserId = "user_${System.currentTimeMillis()}"
    
    // 处理验证结果
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
                snackbarMessage = "人脸验证成功！"
                showSnackbar = true
                onVerificationSuccess(state.result)
            }
            is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
                snackbarMessage = state.message
                showSnackbar = true
            }
            is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
                snackbarMessage = "用户取消了人脸验证"
                showSnackbar = true
            }
            else -> { /* 其他状态不需要处理 */ }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("智能人脸验证") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            if (showSnackbar) {
                Snackbar(
                    action = {
                        TextButton(onClick = { showSnackbar = false }) {
                            Text("确定")
                        }
                    }
                ) {
                    Text(snackbarMessage)
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 功能说明
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "智能人脸验证",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "• 自动获取腾讯云签名参数\n• 无需手动配置复杂参数\n• 一键完成人脸验证流程",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 验证状态显示
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (val state = uiState) {
                        is FaceVerificationViewModel.FaceVerifyUiState.Idle -> {
                            Text(
                                text = "准备开始智能人脸验证",
                                style = MaterialTheme.typography.titleMedium
                            )
                            
                            Button(
                                onClick = {
                                    viewModel.startFaceVerificationWithAutoSign(
                                        config = config,
                                        faceId = currentFaceId,
                                        orderNo = currentOrderNo,
                                        userId = currentUserId
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("开始智能验证")
                            }
                        }
                        
                        is FaceVerificationViewModel.FaceVerifyUiState.Initializing -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Column {
                                    Text(
                                        text = "正在初始化...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "获取签名参数中",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        is FaceVerificationViewModel.FaceVerifyUiState.Verifying -> {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Column {
                                    Text(
                                        text = "正在进行人脸验证...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "请按照提示完成人脸验证",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        
                        is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
                            Text(
                                text = "✓ 人脸验证成功",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("重新验证")
                            }
                        }
                        
                        is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
                            Text(
                                text = "✗ 验证失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                            
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.clearError() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("取消")
                                }
                                
                                Button(
                                    onClick = {
                                        viewModel.startFaceVerificationWithAutoSign(
                                            config = config,
                                            faceId = currentFaceId,
                                            orderNo = currentOrderNo,
                                            userId = currentUserId
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("重试")
                                }
                            }
                        }
                        
                        is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
                            Text(
                                text = "用户取消了验证",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Button(
                                onClick = { viewModel.resetState() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("重新开始")
                            }
                        }
                    }
                }
            }
            
            // 参数信息（仅用于调试）
            if (BuildConfig.DEBUG) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "调试信息",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "App ID: ${config.appId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Face ID: $currentFaceId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Order No: $currentOrderNo",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "User ID: $currentUserId",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}