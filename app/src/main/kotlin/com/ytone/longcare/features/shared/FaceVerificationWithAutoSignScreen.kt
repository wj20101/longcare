package com.ytone.longcare.features.shared

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.domain.faceauth.model.FaceVerifyResult
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.home.vm.HomeSharedViewModel
import com.ytone.longcare.features.shared.vm.FaceVerificationViewModel
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 带自动签名的人脸验证页面（拍照自带源比对方案）
 * 用户先拍摄一张人脸照片作为比对源，然后进行人脸验证
 * 
 * @param onNavigateBack 返回回调
 * @param onVerificationSuccess 验证成功回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceVerificationWithAutoSignScreen(
    navController: NavController,
    onNavigateBack: () -> Unit,
    onVerificationSuccess: (FaceVerifyResult) -> Unit,
    viewModel: FaceVerificationViewModel = hiltViewModel()
) {
    val homeSharedViewModel: HomeSharedViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val user by homeSharedViewModel.userState.collectAsStateWithLifecycle()
    var showSnackbar by remember { mutableStateOf(false) }
    var snackbarMessage by remember { mutableStateOf("") }
    val context = LocalContext.current
    
    // 拍照相关状态
    var capturedPhoto by remember { mutableStateOf<Bitmap?>(null) }
    var sourcePhotoBase64 by remember { mutableStateOf<String?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }
    var showFaceCapture by remember { mutableStateOf(false) }
    val currentUserId = user?.userId?.toString()

    val startVerification = {
        val sourcePhoto = sourcePhotoBase64
        if (sourcePhoto == null) {
            snackbarMessage = "请先拍摄人脸照片"
            showSnackbar = true
        } else if (currentUserId.isNullOrBlank()) {
            snackbarMessage = "用户信息不可用，请重新登录后重试"
            showSnackbar = true
        } else {
            viewModel.startFaceVerificationWithAutoSign(
                context = context,
                orderNo = "order_${System.currentTimeMillis()}",
                userId = currentUserId,
                sourcePhotoStr = sourcePhoto
            )
        }
    }
    
    // 处理人脸捕获结果
    val handleFaceCaptured = { imagePath: String ->
        isProcessingPhoto = true
        try {
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                if (bitmap != null) {
                    capturedPhoto = bitmap
                    
                    // 转换为Base64
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
                    val imageBytes = outputStream.toByteArray()
                    sourcePhotoBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
                    outputStream.close()
                    
                    showFaceCapture = false
                } else {
                    snackbarMessage = "图片处理失败"
                    showSnackbar = true
                }
            } else {
                snackbarMessage = "图片文件不存在"
                showSnackbar = true
            }
        } catch (e: Exception) {
            snackbarMessage = "图片处理失败: ${e.message}"
            showSnackbar = true
        } finally {
            isProcessingPhoto = false
        }
    }
    
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
    
    // 如果显示人脸捕获界面
    if (showFaceCapture) {
        ManualFaceCaptureScreen(
            onNavigateBack = { showFaceCapture = false },
            onFaceCaptured = handleFaceCaptured
        )
        return
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
                        text = "拍照人脸验证",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "• 先拍摄一张清晰的人脸照片\n• 系统将使用此照片进行比对验证\n• 确保光线充足，面部清晰可见",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            // 拍照区域
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "第一步：拍摄人脸照片",
                        style = MaterialTheme.typography.titleMedium
                    )
                    
                    // 显示拍摄的照片或拍照按钮
                    if (capturedPhoto != null) {
                        Image(
                            bitmap = capturedPhoto!!.asImageBitmap(),
                            contentDescription = "拍摄的人脸照片",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    capturedPhoto = null
                                    sourcePhotoBase64 = null
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("重新拍照")
                            }
                        }
                    } else {
                        Button(
                            onClick = {
                                showFaceCapture = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isProcessingPhoto
                        ) {
                            if (isProcessingPhoto) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text(
                                text = if (isProcessingPhoto) "处理中..." else "拍摄人脸照片",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            
            // 验证区域
            if (sourcePhotoBase64 != null) {
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
                        Text(
                            text = "第二步：开始人脸验证",
                            style = MaterialTheme.typography.titleMedium
                        )
                        
                        when (val state = uiState) {
                            is FaceVerificationViewModel.FaceVerifyUiState.Idle -> {
                                Text(
                                    text = "准备开始人脸验证",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                
                                Button(
                                    onClick = startVerification,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !currentUserId.isNullOrBlank()
                                ) {
                                    Text("开始验证")
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
                                    onClick = { 
                                        viewModel.resetState()
                                        capturedPhoto = null
                                        sourcePhotoBase64 = null
                                    },
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
                                        onClick = startVerification,
                                        modifier = Modifier.weight(1f),
                                        enabled = !currentUserId.isNullOrBlank()
                                    ) {
                                        Text("重试")
                                    }
                                }
                            }
                            
                            is FaceVerificationViewModel.FaceVerifyUiState.Cancelled -> {
                                Text(
                                    text = "✗ 验证已取消",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                
                                Button(
                                    onClick = startVerification,
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !currentUserId.isNullOrBlank()
                                ) {
                                    Text("重新开始验证")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
