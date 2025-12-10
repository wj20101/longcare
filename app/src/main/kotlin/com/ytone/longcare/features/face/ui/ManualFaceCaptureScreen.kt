package com.ytone.longcare.features.face.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ytone.longcare.theme.PrimaryBlue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.face.viewmodel.ManualFaceCaptureViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFaceCaptureScreen(
    onNavigateBack: () -> Unit,
    onFaceCaptured: (String) -> Unit,
    viewModel: ManualFaceCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentState by viewModel.currentState.collectAsStateWithLifecycle()
    
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    // 相机权限请求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setCameraPermissionGranted(isGranted)
    }
    
    // 检查并请求相机权限
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permission == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            viewModel.setCameraPermissionGranted(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    // 监听成功状态，返回结果
    LaunchedEffect(currentState) {
        if (currentState is ManualFaceCaptureState.Success) {
            val savedPath = uiState.savedFaceImagePath
            if (savedPath != null) {
                onFaceCaptured(savedPath)
            }
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("拍照") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PrimaryBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                !uiState.cameraPermissionGranted -> {
                    PermissionDeniedContent(
                        onRequestPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    )
                }
                
                uiState.capturedPhoto == null -> {
                    CameraPreviewContent(
                        onImageCapture = { capture ->
                            imageCapture = capture
                        },
                        onTakePhoto = {
                            viewModel.startCapture()
                            takePhoto(imageCapture, viewModel)
                        },
                        isCapturing = currentState is ManualFaceCaptureState.CapturingPhoto
                    )
                }
                
                else -> {
                    PhotoReviewContent(
                        bitmap = uiState.capturedPhoto,
                        detectedFaces = uiState.detectedFaces,
                        selectedFaceIndex = uiState.selectedFaceIndex,
                        isProcessingFaces = uiState.isProcessingFaces,
                        onFaceSelected = viewModel::selectFace,
                        onRetakePhoto = viewModel::resetState,
                        currentState = currentState
                    )
                }
            }
            
            // 错误信息显示
            uiState.errorMessage?.let { error ->
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = viewModel::clearError) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "关闭",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }
            
            // 加载指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("处理中...")
                        }
                    }
                }
            }
        }
    }
    
    // 确认对话框
    if (uiState.showConfirmationDialog) {
        FaceConfirmationDialog(
            selectedFace = uiState.selectedFaceIndex?.let { index ->
                uiState.detectedFaces.getOrNull(index)
            },
            qualityHints = uiState.selectedFaceIndex?.let { index ->
                viewModel.getFaceQualityHints(index)
            } ?: emptyList(),
            onConfirm = viewModel::confirmSelectedFace,
            onCancel = viewModel::cancelAndRetake,
            onDismiss = viewModel::hideConfirmationDialog
        )
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "需要相机权限",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "请授予相机权限以使用人脸捕获功能",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("授予权限")
        }
    }
}

@Composable
private fun CameraPreviewContent(
    onImageCapture: (ImageCapture) -> Unit,
    onTakePhoto: () -> Unit,
    isCapturing: Boolean
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // 使用remember来保持相机提供者的引用
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    
    // 在组件销毁时清理资源
    DisposableEffect(lifecycleOwner) {
        onDispose {
            cameraProvider?.unbindAll()
        }
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val provider = cameraProviderFuture.get()
                            cameraProvider = provider
                            
                            val preview = Preview.Builder()
                                .build()
                            
                            val imageCapture = ImageCapture.Builder()
                                .setTargetRotation(display.rotation)
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            
                            onImageCapture(imageCapture)
                            
                            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                            
                            // 先解绑所有用例
                            provider.unbindAll()
                            
                            // 绑定到生命周期
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                            
                            // 设置预览表面提供者
                            preview.surfaceProvider = surfaceProvider
                            
                        } catch (exc: Exception) {
                            // 记录错误但不崩溃
                            android.util.Log.e("CameraPreview", "相机初始化失败", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 拍照按钮
        FloatingActionButton(
            onClick = onTakePhoto,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .size(72.dp),
            containerColor = PrimaryBlue,
            contentColor = Color.White
        ) {
            if (isCapturing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.White,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "拍照",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        
        // 拍照提示
        Card(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Text(
                text = "请正对相机，点击下方按钮拍照",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PhotoReviewContent(
    bitmap: Bitmap?,
    detectedFaces: List<DetectedFace>,
    selectedFaceIndex: Int?,
    isProcessingFaces: Boolean,
    onFaceSelected: (Int) -> Unit,
    onRetakePhoto: () -> Unit,
    currentState: ManualFaceCaptureState
) {
    // 添加全屏预览状态
    var showFullScreenPreview by remember { mutableStateOf(false) }
    var fullScreenFace by remember { mutableStateOf<DetectedFace?>(null) }
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 照片显示区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
        ) {
            bitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "拍摄的照片",
                    modifier = Modifier.fillMaxSize()
                )
                
                // 绘制人脸边框
                Canvas(modifier = Modifier.fillMaxSize()) {
                    detectedFaces.forEachIndexed { index, face ->
                        val isSelected = index == selectedFaceIndex
                        val color = if (isSelected) Color.Green else Color.Red
                        val strokeWidth = if (isSelected) 6.dp.toPx() else 3.dp.toPx()
                        
                        drawRect(
                            color = color,
                            topLeft = Offset(
                                face.boundingBox.left.toFloat(),
                                face.boundingBox.top.toFloat()
                            ),
                            size = Size(
                                face.boundingBox.width().toFloat(),
                                face.boundingBox.height().toFloat()
                            ),
                            style = Stroke(width = strokeWidth)
                        )
                    }
                }
            }
            
            if (isProcessingFaces) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在检测人脸...")
                        }
                    }
                }
            }
        }
        
        // 底部控制区域
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                when (currentState) {
                    is ManualFaceCaptureState.NoFacesDetected -> {
                        Text(
                            text = "未检测到人脸",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "请确保光线充足，面部清晰可见",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    
                    is ManualFaceCaptureState.FacesDetected -> {
                        Text(
                            text = "检测到 ${detectedFaces.size} 张人脸，请选择一张",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            itemsIndexed(detectedFaces) { index, face ->
                                FaceSelectionItem(
                                    face = face,
                                    isSelected = index == selectedFaceIndex,
                                    onClick = { onFaceSelected(index) },
                                    onLongClick = {
                                        fullScreenFace = face
                                        showFullScreenPreview = true
                                    }
                                )
                            }
                        }
                    }
                    
                    else -> {
                        if (detectedFaces.size == 1) {
                            Text(
                                text = "检测到人脸",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onRetakePhoto,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重新拍照")
                    }
                }
            }
        }
    }
    
    // 全屏预览对话框
    if (showFullScreenPreview && fullScreenFace != null) {
        FaceFullScreenPreviewDialog(
            face = fullScreenFace!!,
            onDismiss = {
                showFullScreenPreview = false
                fullScreenFace = null
            }
        )
    }
}

@Composable
private fun FaceSelectionItem(
    face: DetectedFace,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) PrimaryBlue else Color.Gray,
                shape = RoundedCornerShape(8.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Image(
            bitmap = face.croppedFace.asImageBitmap(),
            contentDescription = "人脸",
            modifier = Modifier.fillMaxSize()
        )
        
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PrimaryBlue.copy(alpha = 0.3f))
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }
        
    }
}

@Composable
private fun FaceFullScreenPreviewDialog(
    face: DetectedFace,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // 放大的人脸图片
                Card(
                    modifier = Modifier
                        .size(300.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Image(
                        bitmap = face.croppedFace.asImageBitmap(),
                        contentDescription = "人脸预览",
                        modifier = Modifier.fillMaxSize()
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 关闭按钮
                FloatingActionButton(
                    onClick = onDismiss,
                    containerColor = Color.White,
                    contentColor = Color.Black
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "关闭",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FaceConfirmationDialog(
    selectedFace: DetectedFace?,
    qualityHints: List<String>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    // 添加全屏预览状态
    var showFullScreenPreview by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认选择的人脸") },
        text = {
            Column {
                selectedFace?.let { face ->
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .align(Alignment.CenterHorizontally)
                            .clickable { showFullScreenPreview = true }
                    ) {
                        Image(
                            bitmap = face.croppedFace.asImageBitmap(),
                            contentDescription = "选择的人脸",
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // 添加放大提示图标
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .background(
                                    Color.Black.copy(alpha = 0.6f),
                                    CircleShape
                                )
                                .padding(4.dp)
                        ) {
                            Text(
                                text = "🔍",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (qualityHints.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        qualityHints.forEach { hint ->
                            Text(
                                text = "• $hint",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text("重新拍照")
            }
        }
    )
    
    // 全屏预览对话框
    if (showFullScreenPreview && selectedFace != null) {
        FaceFullScreenPreviewDialog(
            face = selectedFace,
            onDismiss = { showFullScreenPreview = false }
        )
    }
}

private fun takePhoto(
    imageCapture: ImageCapture?,
    viewModel: ManualFaceCaptureViewModel
) {
    imageCapture?.takePicture(
        Executors.newSingleThreadExecutor(),
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    // 从ImageProxy转换为Bitmap
                    val originalBitmap = imageProxyToBitmap(image)

                    // 获取旋转角度
                    val rotationDegrees = image.imageInfo.rotationDegrees

                    // 处理前置摄像头的图片方向和镜像
                    val correctedBitmap = correctImageOrientation(originalBitmap, rotationDegrees)
                    viewModel.onPhotoCaptured(correctedBitmap)
                } catch (e: Exception) {
                    Log.e("CameraCapture", "图片处理失败", e)
                } finally {
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                Log.e("CameraCapture", "拍照失败", exception)
            }
        }
    )
}

/**
 * 从ImageProxy转换为Bitmap，并进行适当的下采样以避免内存溢出
 */
private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val planeProxy = image.planes[0]
    val buffer: java.nio.ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    // 1. 先只解码尺寸
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = true
    }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

    // 2. 计算采样率，限制最大宽高为 2048 (4MP左右，足够人脸识别和显示)
    options.inSampleSize = calculateInSampleSize(options, 2048, 2048)
    options.inJustDecodeBounds = false

    // 3. 实际解码
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        ?: throw IllegalStateException("无法解码图片")
}

/**
 * 计算采样率
 */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // 计算最大的 inSampleSize 值，该值是 2 的幂，且保持宽高均大于请求的宽高
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }

    return inSampleSize
}

/**
 * 修正图片方向
 * 参考CameraScreen.kt的rotateBitmap实现，只做旋转处理
 */
private fun correctImageOrientation(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees.toFloat())
    
    return try {
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        ).also {
            // 回收原始bitmap以释放内存
            if (it != bitmap) {
                bitmap.recycle()
            }
        }
    } catch (e: Exception) {
        Log.e("ImageCorrection", "图片方向修正失败", e)
        bitmap // 如果修正失败，返回原始图片
    }
}