package com.ytone.longcare.features.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tencent.cloud.huiyansdkface.facelight.api.result.WbFaceVerifyResult
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.FaceVerificationManager
import com.ytone.longcare.ui.components.FaceVerificationComponent

/**
 * 人脸验证页面
 * 这是一个示例页面，展示如何使用人脸验证组件
 * 
 * @param onNavigateBack 返回回调
 * @param onVerificationSuccess 验证成功回调
 * @param faceVerifyParams 人脸验证参数（可选，用于预设参数）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationScreen(
    onNavigateBack: () -> Unit,
    onVerificationSuccess: (WbFaceVerifyResult) -> Unit,
    faceVerifyParams: FaceVerificationManager.FaceVerifyParams? = null
) {
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    
    // 示例参数（实际使用时应该从服务器获取）
    val defaultParams = remember {
        FaceVerificationManager.FaceVerifyParams(
            faceId = "your_face_id",
            orderNo = "your_order_no",
            appId = "your_app_id",
            nonce = "your_nonce",
            userId = "your_user_id",
            sign = "your_sign",
            keyLicence = "your_key_licence"
        )
    }
    
    val params = faceVerifyParams ?: defaultParams
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("人脸验证") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
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
            // 说明文字
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
                        text = "人脸验证说明",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "• 请确保光线充足\n• 保持面部正对摄像头\n• 按照提示完成相应动作",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 人脸验证组件
            FaceVerificationComponent(
                params = params,
                onSuccess = { result ->
                    snackbarMessage = "人脸验证成功！"
                    showSnackbar = true
                    onVerificationSuccess(result)
                },
                onError = { message ->
                    snackbarMessage = message
                    showSnackbar = true
                },
                onCancel = {
                    snackbarMessage = "用户取消了人脸验证"
                    showSnackbar = true
                }
            )
            
            // 参数信息（仅用于调试，生产环境应移除）
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
                            text = "Face ID: ${params.faceId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Order No: ${params.orderNo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "User ID: ${params.userId}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}