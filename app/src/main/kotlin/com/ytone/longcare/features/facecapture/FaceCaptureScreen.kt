package com.ytone.longcare.features.facecapture

import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import java.util.concurrent.Executors

/**
 * 人脸捕获主界面
 * 使用最新的CameraX Compose组件和现代化UI设计
 * 
 * @param onFaceSelected 选择人脸后的回调
 * @param onNavigateBack 返回导航回调
 * @param viewModel ViewModel实例
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun FaceCaptureScreen(
    onFaceSelected: (Bitmap) -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: FaceCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    // 图片预览状态
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // 相机权限状态
    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    
    // 权限请求启动器
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
            if (!granted) {
                viewModel.setError("需要相机权限才能使用人脸捕获功能")
            }
        }
    )
    
    // 请求相机权限
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCamPermission) {
        // 检查前置摄像头是否可用，不可用则回退到后置摄像头
        val availableCameraSelector = remember {
            getAvailableCameraSelector(context)
        }

        val analyzerScope = rememberCoroutineScope()
        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
        val analyzer = remember(viewModel, analyzerScope) {
            FaceCaptureAnalyzer(
                onFaceCaptured = { bitmap, quality ->
                    viewModel.onFaceCaptured(bitmap, quality)
                },
                onProcessingStateChanged = { isProcessing ->
                    viewModel.updateProcessingState(isProcessing)
                },
                onHintChanged = { hint ->
                    viewModel.updateUserHint(hint)
                },
                onFaceDetectionChanged = { detected, quality ->
                    viewModel.updateFaceDetectionState(detected, quality)
                },
                coroutineScope = analyzerScope
            )
        }
        
        // 创建相机控制器
        val cameraController = remember(availableCameraSelector, analyzer, analysisExecutor) {
            LifecycleCameraController(context).apply {
                setImageAnalysisAnalyzer(
                    analysisExecutor,
                    analyzer
                )
                
                // 优化设置
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                cameraSelector = availableCameraSelector
                setEnabledUseCases(
                    CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE
                )
            }
        }

        DisposableEffect(cameraController, analyzer, analysisExecutor) {
            onDispose {
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                analyzer.release()
                analysisExecutor.shutdown()
            }
        }

        // 绑定相机生命周期
        LaunchedEffect(lifecycleOwner, cameraController) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 相机预览
            AndroidView(
                factory = { context ->
                    androidx.camera.view.PreviewView(context).apply {
                        this.controller = cameraController
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { 
                        contentDescription = "相机预览，用于人脸捕获" 
                    }
            )
            
            // 顶部工具栏
            TopAppBar(
                title = { 
                    Text(
                        text = "人脸捕获",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    if (uiState.hasCapturedFaces) {
                        IconButton(onClick = { viewModel.clearAllFaces() }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "清空所有照片",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.7f)
                ),
                modifier = Modifier.align(Alignment.TopCenter)
            )
            
            // 人脸检测指示器
            FaceDetectionIndicator(
                detected = uiState.faceDetected,
                quality = uiState.faceQuality,
                modifier = Modifier.align(Alignment.Center)
            )
            
            // 底部UI
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .background(
                        Color.Black.copy(alpha = 0.8f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 用户提示
                AnimatedContent(
                    targetState = uiState.userHint,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) + slideInVertically() togetherWith
                        fadeOut(animationSpec = tween(300)) + slideOutVertically()
                    },
                    label = "hint_animation"
                ) { hint ->
                    Text(
                        text = hint,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // 已捕获的人脸列表
                if (uiState.hasCapturedFaces) {
                    Text(
                        text = "已捕获 ${uiState.capturedFaces.size}/${FaceCaptureUiState.MAX_FACES} 张照片",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp) // Adjusted padding
                    ) {
                        itemsIndexed(uiState.capturedFaces) { index, bitmap ->
                            val isSelected = index == uiState.selectedFaceIndex
                            FaceThumbnail(
                                bitmap = bitmap,
                                isSelected = isSelected,
                                onClick = {
                                    if (isSelected) {
                                        previewBitmap = bitmap
                                    } else {
                                        viewModel.selectFace(index)
                                    }
                                },
                                onDelete = {
                                    viewModel.removeFace(index)
                                }
                            )
                        }
                    }
                    
                    Text(
                        text = "提示: 单击选择, 再次单击预览, 点击右上角删除",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
                
                // 操作按钮
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.hasSelectedFace) {
                        // 确认选择按钮
                        Button(
                            onClick = {
                                uiState.selectedFace?.let { bitmap ->
                                    onFaceSelected(bitmap)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("确认选择")
                        }
                        
                        // 取消选择按钮
                        OutlinedButton(
                            onClick = { viewModel.cancelSelection() },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(1.dp, Color.White)
                        ) {
                            Text("重新选择")
                        }
                    } else {
                        // 捕获状态指示
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.isCapturing) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = "正在捕获",
                                    tint = if (uiState.faceDetected) 
                                        MaterialTheme.colorScheme.primary 
                                    else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (uiState.faceDetected) "检测到人脸" else "寻找人脸中...",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
            
            // 错误提示
            uiState.error?.let { error ->
                LaunchedEffect(error) {
                    // 可以在这里显示Snackbar或其他错误提示
                }
            }
            
            // 图片预览Dialog
            previewBitmap?.let { bitmap ->
                ImagePreviewDialog(
                    bitmap = bitmap,
                    onDismiss = { previewBitmap = null }
                )
            }
        }
    } else {
        // 权限被拒绝的UI
        PermissionDeniedScreen(
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onNavigateBack = onNavigateBack
        )
    }
}

/**
 * 获取可用的相机选择器
 * 优先使用前置摄像头，如果不可用则回退到后置摄像头
 * 
 * @param context 上下文
 * @return 可用的 CameraSelector
 */
private fun getAvailableCameraSelector(context: Context): CameraSelector {
    return try {
        val cameraManager = context.getSystemService<CameraManager>()
            ?: return CameraSelector.DEFAULT_FRONT_CAMERA
        val cameraIds = cameraManager.cameraIdList
        
        // 检查是否有前置摄像头
        val hasFrontCamera = cameraIds.any { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_FRONT
        }
        
        if (hasFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            // 没有前置摄像头，使用后置摄像头
            com.ytone.longcare.common.utils.KLogger.w("FaceCaptureScreen", "前置摄像头不可用，回退到后置摄像头")
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    } catch (e: Exception) {
        com.ytone.longcare.common.utils.KLogger.e("FaceCaptureScreen", "检测相机失败: ${e.message}", e)
        // 发生错误时默认尝试前置摄像头
        CameraSelector.DEFAULT_FRONT_CAMERA
    }
}
