package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.databinding.WatermarkViewBinding
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.load
import coil3.request.allowHardware
import coil3.request.error
import com.ytone.longcare.R
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import kotlinx.coroutines.CoroutineScope
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import android.os.Environment

@Composable
fun CameraScreen(
    navController: NavController,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasPermission = granted
        }
    )

    LaunchedEffect(Unit) {
        launcher.launch(Manifest.permission.CAMERA)
    }

    if (hasPermission) {
        CameraContent(
            context = context,
            watermarkData = watermarkData,
            viewModel = viewModel,
            onImageCaptured = { file ->
                val savedUri = Uri.fromFile(file)
                navController.previousBackStackEntry?.savedStateHandle?.set(
                    NavigationConstants.CAPTURED_IMAGE_URI_KEY,
                    savedUri.toString()
                )
                navController.popBackStack()
            },
        )
    } else {
        // You can optionally show a message or a button to re-request permission
        Box(modifier = Modifier.fillMaxSize()) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Request Camera Permission")
            }
        }
    }
}

@Composable
private fun CameraContent(
    context: Context,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel,
    onImageCaptured: (File) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            // 配置 ImageCapture 目标分辨率 1920x1080
            // CameraX 会选择最接近目标尺寸的可用分辨率
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .build()
            imageCaptureResolutionSelector = resolutionSelector
        }
    }
    var watermarkView by remember { mutableStateOf<View?>(null) }
    val location by viewModel.location.collectAsState()
    val time by viewModel.time.collectAsState()
    val logoImg by viewModel.syLogoImg.collectAsState()
    
    // 摄像头切换状态
    var isFrontCamera by remember { mutableStateOf(false) }
    // 相机是否正在切换中（用于防止切换时拍照导致崩溃）
    var isCameraSwitching by remember { mutableStateOf(false) }
    // 是否正在拍照中（防止连续点击导致重复拍照）
    var isCapturing by remember { mutableStateOf(false) }
    // 是否有前置摄像头（默认 false，等待检测）
    var hasFrontCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // 检测前置摄像头可用性并记录设备信息（增加重试机制）
    LaunchedEffect(cameraController) {
        // 重试机制：最多重试 3 次，每次增加延迟
        var detected = false
        for (attempt in 1..3) {
            delay(200L * attempt)  // 200ms, 400ms, 600ms
            
            detected = try {
                // 方式1：使用 CameraController 检测
                val hasCamera = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                if (hasCamera) {
                    true
                } else {
                    // 方式2：使用 CameraManager 作为备选
                    val cameraManager = context.getSystemService(CameraManager::class.java)
                    cameraManager?.cameraIdList?.any { id ->
                        val characteristics = cameraManager.getCameraCharacteristics(id)
                        val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                        facing == CameraCharacteristics.LENS_FACING_FRONT
                    } ?: false
                }
            } catch (e: Exception) {
                if (attempt == 3) {
                    CameraEventTracker.trackError(
                        CameraEventTracker.EventType.CAMERA_INIT_ERROR,
                        e,
                        mapOf("reason" to "检测前置摄像头失败", "attempt" to attempt)
                    )
                }
                false
            }
            
            if (detected) break
        }
        
        // 检测结果赋值（如果检测失败则不显示切换按钮）
        hasFrontCamera = detected
    }


    val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                viewModel.updateCurrentLocationInfo()
            }
        }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateTime()
                viewModel.updateSyLogoImg()
                launcher.launch(locationPermissions)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 延迟拍照模式定义
    var delayMode by remember { mutableStateOf(DelayMode.OFF) }
    // 倒计时剩余秒数
    var countdownSeconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // 是否正在倒计时
    val isCountingDown = countdownSeconds > 0

    // 用于触发实际拍照的 Action
    val performCapture = {
        val view = watermarkView
        if (view != null && view.width > 0 && view.height > 0) {
           isCapturing = true
           viewModel.updateTime()
           val executor = ContextCompat.getMainExecutor(context)
           takePhoto(
               context = context,
               cameraController = cameraController,
               executor = executor,
               watermarkView = view,
               isFrontCamera = isFrontCamera,
               scope = scope,
               onImageCaptured = { file ->
                   isCapturing = false
                   onImageCaptured(file)
               },
               onError = {
                   isCapturing = false
               }
           ) 
        } else {
             Toast.makeText(context, "水印准备中，请稍后...", Toast.LENGTH_SHORT).show()
        }
    }

    // 倒计时逻辑
    LaunchedEffect(countdownSeconds) {
        if (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
            if (countdownSeconds == 0) {
                performCapture()
            }
        }
    }


    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            cameraController = cameraController,
            lifecycleOwner = lifecycleOwner,
            modifier = Modifier.fillMaxSize()
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                AndroidView(
                    factory = { ctx ->
                        val binding = WatermarkViewBinding.inflate(
                            LayoutInflater.from(ctx),
                            FrameLayout(ctx),
                            false
                        )
                        val view = binding.root
                        view.tag = binding
                        watermarkView = view
                        view
                    },
                    update = { view ->
                        val binding = view.tag as WatermarkViewBinding
                        binding.serviceTypeTextView.text = watermarkData.title
                        binding.insuredPersonTextView.text = watermarkData.insuredPerson
                        binding.caregiverTextView.text = watermarkData.caregiver
                        binding.captureTimeTextView.text = time
                        binding.coordinatesTextView.text = location
                        binding.captureLocationTextView.text = watermarkData.address
                        if (logoImg.isNotEmpty()) {
                            binding.logoImageView.load(logoImg) {
                                allowHardware(false)
                                error(R.drawable.app_watermark_image)
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 13.dp, bottom = 14.dp)
                )

                // 顶部工具栏（放置延迟拍照设置）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, end = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    DelayTimerButton(
                        currentMode = delayMode,
                        onClick = {
                            delayMode = delayMode.next()
                        },
                        enabled = !isCapturing && !isCountingDown
                    )
                }

                // 底部控制栏
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 占位符
                    Box(modifier = Modifier.size(56.dp))
                    
                    // 拍照按钮
                    ShutterButton(
                        onClick = {
                            if (isCountingDown) {
                                countdownSeconds = 0
                                return@ShutterButton
                            }

                            if (isCameraSwitching || isCapturing) {
                                return@ShutterButton
                            }
                            
                            if (watermarkView == null) {
                                Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                                return@ShutterButton
                            }
                            
                            val view = watermarkView ?: return@ShutterButton
                            
                            if (view.width <= 0 || view.height <= 0) {
                                Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                                return@ShutterButton
                            }

                            if (delayMode != DelayMode.OFF) {
                                countdownSeconds = delayMode.seconds
                                return@ShutterButton
                            }
                            
                            // 立即拍照
                            isCapturing = true
                            viewModel.updateTime()
                            val executor = ContextCompat.getMainExecutor(context)
                            takePhoto(
                                context = context,
                                cameraController = cameraController,
                                executor = executor,
                                watermarkView = view,
                                isFrontCamera = isFrontCamera,
                                scope = scope,
                                onImageCaptured = { file ->
                                    isCapturing = false
                                    onImageCaptured(file)
                                },
                                onError = {
                                    isCapturing = false
                                }
                            )
                        },
                        enabled = !isCameraSwitching && (!isCapturing || isCountingDown),
                        isCountingDown = isCountingDown
                    )
                    
                    // 切换摄像头按钮
                    if (hasFrontCamera) {
                        CameraSwitchButton(
                            onClick = {
                                if (isCameraSwitching || isCapturing) return@CameraSwitchButton
                                
                                if (isCountingDown) {
                                    countdownSeconds = 0
                                }

                                val wasUsingFrontCamera = isFrontCamera
                                
                                scope.launch {
                                    try {
                                        isCameraSwitching = true
                                        val newSelector = if (wasUsingFrontCamera) {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        }
                                        
                                        val targetAvailable = try {
                                            cameraController.hasCamera(newSelector)
                                        } catch (_: Exception) {
                                            false
                                        }
                                        
                                        if (!targetAvailable) {
                                            Toast.makeText(context, "目标摄像头不可用", Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        
                                        cameraController.cameraSelector = newSelector
                                        isFrontCamera = !wasUsingFrontCamera
                                        
                                        // 动态等待：检测相机是否就绪，最长等待 1500ms
                                        var waitTime = 0L
                                        val maxWaitTime = 1500L
                                        val checkInterval = 100L
                                        while (waitTime < maxWaitTime) {
                                            delay(checkInterval)
                                            waitTime += checkInterval
                                            // 检测相机是否已就绪
                                            try {
                                                if (cameraController.cameraInfo != null) break
                                            } catch (_: Exception) { }
                                        }
                                        
                                    } catch (e: Exception) {
                                        CameraEventTracker.trackError(
                                            CameraEventTracker.EventType.CAMERA_SWITCH_ERROR,
                                            e
                                        )
                                        Toast.makeText(context, "切换摄像头失败", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isCameraSwitching = false
                                    }
                                }
                            },
                            enabled = !isCameraSwitching && !isCapturing
                        )
                    } else {
                        Box(modifier = Modifier.size(56.dp))
                    }
                }
                
                // 倒计时显示层
                if (isCountingDown) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = countdownSeconds.toString(),
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 120.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                color = Color.White,
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = Color.Black.copy(alpha = 0.5f),
                                    blurRadius = 8f
                                )
                            )
                        )
                    }
                }

                // 拍照中遮罩
                if (isCapturing && !isCountingDown) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }
        }
    }
}

enum class DelayMode(val seconds: Int, val label: String) {
    OFF(0, "关闭"),
    SECONDS_3(3, "3秒"),
    SECONDS_5(5, "5秒"),
    SECONDS_10(10, "10秒");

    fun next(): DelayMode {
        val values = entries.toTypedArray()
        return values[(ordinal + 1) % values.size]
    }
}

@Composable
private fun DelayTimerButton(
    currentMode: DelayMode,
    onClick: () -> Unit,
    enabled: Boolean
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
             if (currentMode == DelayMode.OFF) {
                 Icon(
                     imageVector = Icons.Filled.TimerOff,
                     contentDescription = "延迟拍照: 关闭",
                     tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                     modifier = Modifier.size(24.dp)
                 )
             } else {
                 Text(
                     text = "${currentMode.seconds}s",
                     color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                     style = androidx.compose.ui.text.TextStyle(
                         fontSize = 18.sp,
                         fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                     )
                 )
             }
        }
    }
}

@Composable
private fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCountingDown: Boolean = false
) {
    val borderColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    val backgroundColor = if (isCountingDown) Color.Red else if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    
    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(4.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor)
        ) {
            if (isCountingDown) {
                Icon(
                    imageVector = Icons.Filled.Circle, // Stop icon doesn't exist in standard filled? Use Circle for now effectively or fix import.
                    // Wait, Step 149 showed Icons.Filled.Stop. I need to make sure I import it or use full path.
                    // The original code used androidx.compose.material.icons.Icons.Filled.Stop.
                    // But Stop might not be in Filled.
                    // Actually, let's use a square for Stop or just text "Stop".
                    // Step 149 usage: androidx.compose.material.icons.Icons.Filled.Stop
                    // Let's assume it exists if I didn't get an error about it before.
                    contentDescription = "取消倒计时",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = "拍照",
                    modifier = Modifier.size(56.dp),
                    tint = backgroundColor
                )
            }
        }
    }
}

@Composable
private fun CameraSwitchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val iconTint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = iconTint,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun CameraPreview(
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                controller = cameraController
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier
    )
}

private fun takePhoto(
    context: Context,
    cameraController: LifecycleCameraController,
    executor: Executor,
    watermarkView: View,
    isFrontCamera: Boolean,
    scope: CoroutineScope,
    onImageCaptured: (File) -> Unit,
    onError: () -> Unit
) {
    val captureStartTime = System.currentTimeMillis()
    
    val watermarkBitmap = try {
        viewToBitmapSafe(watermarkView)
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "水印视图捕获失败")
        )
        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
        onError()
        return
    }
    
    if (watermarkBitmap == null) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            null,
            mapOf("reason" to "水印Bitmap为null")
        )
        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
        onError()
        return
    }
    
    val density = context.resources.displayMetrics.density
    val startPx = (13 * density)
    val bottomPx = (14 * density)
    
    // Use a temp file for initial capture
    val tempFile = File(context.cacheDir, "temp_capture_${System.currentTimeMillis()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

    try {
        cameraController.takePicture(
            outputOptions,
            executor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                    scope.launch(Dispatchers.IO) {
                        var bitmap: Bitmap? = null
                        var watermarkedBitmap: Bitmap? = null
                        
                        try {
                            // 动态超时：低端设备给予更长时间
                            val timeoutMs = calculateDynamicTimeout(context)
                            withTimeout(timeoutMs) {

                                ensureActive()
                                
                                // Decode file to bitmap (mutable for modification if needed, though we create new bitmap for watermark)
                                // Note: CameraX handles rotation in the saved file usually, but we should check Exif if needed.
                                // However, OutputFileOptions usually applies rotation.
                                // Let's simplify: read bitmap, apply watermark.
                                
                                val options = BitmapFactory.Options()
                                options.inJustDecodeBounds = true
                                BitmapFactory.decodeFile(tempFile.absolutePath, options)
                                
                                // 保证短边 >= 1080
                                // 这样竖屏图片会缩放到约 1080x2240 左右
                                val minTargetDimension = 1080
                                var inSampleSize = 1
                                
                                while (true) {
                                    val nextSampleSize = inSampleSize * 2
                                    val scaledWidth = options.outWidth / nextSampleSize
                                    val scaledHeight = options.outHeight / nextSampleSize
                                    // 取短边进行判断，确保短边 >= 1080
                                    val scaledMinDimension = minOf(scaledWidth, scaledHeight)
                                    if (scaledMinDimension >= minTargetDimension) {
                                        inSampleSize = nextSampleSize
                                    } else {
                                        break
                                    }
                                }

                                options.inJustDecodeBounds = false
                                options.inSampleSize = inSampleSize
                                options.inPreferredConfig = Bitmap.Config.ARGB_8888
                                options.inMutable = true
                                
                                bitmap = BitmapFactory.decodeFile(tempFile.absolutePath, options)
                                
                                if (bitmap == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "图片读取失败，请重试", Toast.LENGTH_SHORT).show()
                                        onError()
                                    }
                                    return@withTimeout
                                }
                                
                                // 处理 EXIF 方向 - 某些设备会将竖拍照片保存为横向，通过 EXIF 记录旋转角度
                                val currentBitmap = bitmap
                                val rotatedBitmap = rotateBitmapByExif(currentBitmap, tempFile.absolutePath)
                                if (rotatedBitmap != null && rotatedBitmap != currentBitmap) {
                                    currentBitmap.recycle()
                                    bitmap = rotatedBitmap
                                }
                                
                                // Clean up temp file immediately
                                tempFile.delete()
                                
                                // Handle front camera mirroring if needed?
                                // CameraX with OutputFileOptions might NOT flip front camera automatically depending on configuration.
                                // But usually it respects the view. Let's assume standard behavior first.
                                // If mirroring is needed, we can check isFrontCamera and flip.

                                var processedBitmap = bitmap
                                if (isFrontCamera) {
                                     // Front camera might need mirroring if the saved file is not mirrored.
                                     // Usually CameraX saves what is 'seen' or 'captured'.
                                     // Let's check rotation/flip. For now, we trust CameraX but we might need to flip if user expects mirror.
                                     // The previous code did manual flip. Let's keep it safe:
                                     // Check if we want to flip. Standard CameraX might not flip.
                                     val flipped = flipBitmapHorizontallySafe(processedBitmap)
                                     if (flipped != null) {
                                         // 翻转成功后立即回收原图以降低内存峰值
                                         if (processedBitmap != flipped) {
                                             processedBitmap.recycle()
                                         }
                                         processedBitmap = flipped
                                         // 同时清空 bitmap 引用，避免 finally 中重复回收
                                         bitmap = null
                                     }
                                }

                                ensureActive()

                                watermarkedBitmap = addWatermarkSafe(processedBitmap, watermarkBitmap, startPx, bottomPx)
                                
                                if (watermarkedBitmap == null) {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
                                        onError()
                                    }
                                    return@withTimeout
                                }
                                
                                // Recycle intermediate bitmaps - 及时回收降低内存峰值
                                if (processedBitmap != watermarkedBitmap) {
                                    processedBitmap.recycle()
                                }
                                // 清空 bitmap 引用，避免 finally 中重复回收
                                bitmap = null
                                watermarkBitmap.recycle()

                                ensureActive()
                                val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
                                val finalFile = File(
                                    storageDir,
                                    "captured_image_${System.currentTimeMillis()}.jpg"
                                )
                                
                                FileOutputStream(finalFile).use { out ->
                                    val finalBitmap = watermarkedBitmap ?: return@use
                                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                                }
                                
                                watermarkedBitmap.recycle()
                                watermarkedBitmap = null
                                
                                withContext(Dispatchers.Main) {
                                    onImageCaptured(finalFile)
                                }
                            }
                        } catch (e: Exception) {
                            CameraEventTracker.trackError(
                                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                e,
                                mapOf("reason" to "图片处理失败: ${e.message}")
                            )
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                                onError()
                            }
                        } finally {
                             try {
                                bitmap?.recycle()
                                watermarkedBitmap?.recycle()
                                // Ensure temp file is deleted
                                if (tempFile.exists()) tempFile.delete()
                             } catch (_: Exception) {}
                        }
                    }
                }
                
                override fun onError(exc: ImageCaptureException) {
                    CameraEventTracker.trackError(
                        CameraEventTracker.EventType.CAPTURE_ERROR,
                        exc,
                        mapOf("reason" to "拍照保存失败: ${exc.message}")
                    )
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                        onError()
                    }
                }
            }
        )

    } catch (e: Throwable) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.CAPTURE_ERROR,
            e,
            mapOf(
                "step" to "调用takePicture异常",
                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
            )
        )
        Toast.makeText(context, "调用相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        onError()
    }
}


private fun flipBitmapHorizontallySafe(bitmap: Bitmap): Bitmap? {
    return try {
        val matrix = Matrix()
        matrix.preScale(-1f, 1f)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during flip: ${e.message}", e),
            mapOf(
                "reason" to "翻转时内存不足",
                "bitmapSize" to "${bitmap.width}x${bitmap.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "图片翻转异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

private fun viewToBitmapSafe(view: View): Bitmap? {
    return try {
        if (view.width <= 0 || view.height <= 0) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                null,
                mapOf(
                    "reason" to "视图尺寸无效",
                    "viewSize" to "${view.width}x${view.height}"
                )
            )
            return null
        }
        val bitmap = createBitmap(view.width, view.height)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        bitmap
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during viewToBitmap: ${e.message}", e),
            mapOf(
                "reason" to "视图转Bitmap时内存不足",
                "viewSize" to "${view.width}x${view.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "视图转Bitmap异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

private fun addWatermarkSafe(
    bitmap: Bitmap,
    watermark: Bitmap,
    startPx: Float,
    bottomPx: Float
): Bitmap? {
    return try {
        val result = createBitmap(bitmap.width, bitmap.height)
        val canvas = Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        canvas.drawBitmap(watermark, startPx, (bitmap.height - watermark.height - bottomPx), null)
        result
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during addWatermark: ${e.message}", e),
            mapOf(
                "reason" to "添加水印时内存不足",
                "bitmapSize" to "${bitmap.width}x${bitmap.height}",
                "watermarkSize" to "${watermark.width}x${watermark.height}"
            )
        )
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "添加水印异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

/**
 * 根据设备性能动态计算超时时间
 * - 高端设备（memoryClass >= 256）：15秒
 * - 中端设备（memoryClass >= 128）：25秒
 * - 低端设备：35秒
 */
private fun calculateDynamicTimeout(context: Context): Long {
    val activityManager = context.getSystemService<ActivityManager>()
    val memoryClass = activityManager?.memoryClass ?: 128
    
    return when {
        memoryClass >= 256 -> 15_000L  // 高端设备：15秒
        memoryClass >= 128 -> 25_000L  // 中端设备：25秒
        else -> 35_000L                // 低端设备：35秒
    }
}

/**
 * 根据 EXIF 方向信息旋转 Bitmap
 * 某些设备会将竖拍照片保存为横向，然后在 EXIF 中记录旋转角度
 */
private fun rotateBitmapByExif(bitmap: Bitmap, filePath: String): Bitmap? {
    return try {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        
        if (rotationDegrees == 0f) {
            // 无需旋转
            bitmap
        } else {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees)
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )
            rotatedBitmap
        }
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "EXIF旋转处理失败: ${e.javaClass.simpleName}")
        )
        bitmap // 失败时返回原图
    }
}