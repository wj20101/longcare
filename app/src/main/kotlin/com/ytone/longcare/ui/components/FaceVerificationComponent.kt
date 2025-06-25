package com.ytone.longcare.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel

/**
 * 人脸验证组件
 * @param params 人脸验证参数
 * @param onSuccess 验证成功回调
 * @param onError 验证失败回调
 * @param onCancel 用户取消回调
 * @param modifier 修饰符
 * @param viewModel 人脸验证ViewModel
 */
@Composable
fun FaceVerificationComponent(
    params: FaceVerificationManager.FaceVerifyParams,
    onSuccess: (WbFaceVerifyResult) -> Unit,
    onError: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FaceVerificationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 处理状态变化
    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
                onSuccess(state.result)
            }
            is FaceVerificationViewModel.FaceVerifyUiState.Error -> {
                onError(state.message)
            }
            is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
                onCancel()
            }
            else -> { /* 其他状态不需要处理 */ }
        }
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = uiState) {
                is FaceVerificationViewModel.FaceVerifyUiState.Idle -> {
                    Text(
                        text = "准备开始人脸验证",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Button(
                        onClick = { viewModel.startFaceVerification(params) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开始人脸验证")
                    }
                }
                
                is FaceVerificationViewModel.FaceVerifyUiState.Initializing -> {
                    CircularProgressIndicator()
                    Text(
                        text = "正在初始化人脸识别...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
                
                is FaceVerificationViewModel.FaceVerifyUiState.Verifying -> {
                    CircularProgressIndicator()
                    Text(
                        text = "正在进行人脸验证...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "请按照提示完成人脸验证",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
                
                is FaceVerificationViewModel.FaceVerifyUiState.Success -> {
                    Text(
                        text = "✓ 人脸验证成功",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center
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
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
                            onClick = { viewModel.startFaceVerification(params) },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
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
}