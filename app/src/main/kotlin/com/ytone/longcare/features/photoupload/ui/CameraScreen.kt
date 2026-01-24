package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
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
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.AspectRatioStrategy
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
import androidx.compose.material.icons.filled.Timer
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
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
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
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
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
            // 记录权限结果
            if (granted) {
                CameraEventTracker.trackEvent(CameraEventTracker.EventType.CAMERA_PERMISSION_GRANTED)
            } else {
                CameraEventTracker.trackEvent(CameraEventTracker.EventType.CAMERA_PERMISSION_DENIED)
            }
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
            // 根据设备性能动态选择分辨率
            val targetResolution = if (isLowEndDevice(context)) {
                Size(1280, 720)  // 低端设备使用 720p
            } else {
                Size(1920, 1080) // 正常设备使用 1080p
            }
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        targetResolution,
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                    )
                )
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                .build()
            this.previewResolutionSelector = resolutionSelector
            this.imageCaptureResolutionSelector = resolutionSelector
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
    
    // 检测前置摄像头可用性并记录设备信息
    LaunchedEffect(cameraController) {
        // 记录相机初始化开始
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.CAMERA_INIT_START,
            mapOf(
                "isLowEndDevice" to isLowEndDevice(context),
                "targetResolution" to if (isLowEndDevice(context)) "720p" else "1080p"
            )
        )
        
        // 等待相机绑定完成
        delay(300)
        
        // 使用多种方式检测前置摄像头
        hasFrontCamera = try {
            // 方式1：使用 CameraController 检测
            val hasCamera = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
            if (hasCamera) {
                true
            } else {
                // 方式2：使用 CameraManager 作为备选
                val cameraManager = context.getSystemService(android.hardware.camera2.CameraManager::class.java)
                cameraManager?.cameraIdList?.any { id ->
                    val characteristics = cameraManager.getCameraCharacteristics(id)
                    val facing = characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING)
                    facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT
                } ?: false
            }
        } catch (e: Exception) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.CAMERA_INIT_ERROR,
                e,
                mapOf("reason" to "检测前置摄像头失败")
            )
            // 检测失败时默认显示切换按钮，让用户尝试
            true
        }
        
        // 记录相机初始化成功
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.CAMERA_INIT_SUCCESS,
            mapOf("hasFrontCamera" to hasFrontCamera)
        )
        
        // 如果是低端设备，记录设备信息
        if (isLowEndDevice(context)) {
            CameraEventTracker.trackEvent(CameraEventTracker.EventType.LOW_END_DEVICE_DETECTED)
        }
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
        // 记录拍照触发
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.CAPTURE_START,
            mapOf(
                "step" to "倒计时结束触发拍照",
                "isFrontCamera" to isFrontCamera
            )
        )

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
                                CameraEventTracker.trackEvent(
                                    CameraEventTracker.EventType.CAMERA_SWITCH_START,
                                    mapOf("from" to if (wasUsingFrontCamera) "front" else "back")
                                )
                                
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
                                        delay(500)
                                        
                                        CameraEventTracker.trackEvent(
                                            CameraEventTracker.EventType.CAMERA_SWITCH_SUCCESS,
                                            mapOf("to" to if (isFrontCamera) "front" else "back")
                                        )
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
                     imageVector = androidx.compose.material.icons.Icons.Filled.TimerOff,
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
                    imageVector = androidx.compose.material.icons.Icons.Filled.Circle, // Stop icon doesn't exist in standard filled? Use Circle for now effectively or fix import.
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
    
    CameraEventTracker.trackEvent(
        CameraEventTracker.EventType.IMAGE_WATERMARK_START,
        mapOf(
            "step" to "捕获水印视图",
            "viewSize" to "${watermarkView.width}x${watermarkView.height}"
        )
    )
    
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
    
    val watermarkCaptureTime = System.currentTimeMillis() - captureStartTime
    CameraEventTracker.trackEvent(
        CameraEventTracker.EventType.IMAGE_WATERMARK_START,
        mapOf(
            "step" to "水印视图捕获完成",
            "watermarkCaptureTimeMs" to watermarkCaptureTime,
            "watermarkSize" to "${watermarkBitmap.width}x${watermarkBitmap.height}"
        )
    )
    
    val density = context.resources.displayMetrics.density
    val startPx = (13 * density)
    val bottomPx = (14 * density)
    
    cameraController.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val callbackTime = System.currentTimeMillis() - captureStartTime
                CameraEventTracker.trackEvent(
                    CameraEventTracker.EventType.CAPTURE_CALLBACK_RECEIVED,
                    mapOf(
                        "callbackTimeMs" to callbackTime,
                        "imageSize" to "${image.width}x${image.height}",
                        "rotationDegrees" to image.imageInfo.rotationDegrees
                    )
                )
                
                scope.launch(Dispatchers.IO) {
                    var bitmap: Bitmap? = null
                    var rotatedBitmap: Bitmap? = null
                    var watermarkedBitmap: Bitmap? = null
                    
                    try {
                        withTimeout(15_000L) {
                            CameraEventTracker.trackEvent(CameraEventTracker.EventType.IMAGE_DECODE_START)
                            ensureActive()
                            
                            val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                            bitmap = imageProxyToBitmapSafe(image, context)
                            
                            if (bitmap == null) {
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                    null,
                                    mapOf(
                                        "reason" to "图片解码返回null",
                                        "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "图片解码失败，请重试", Toast.LENGTH_SHORT).show()
                                    onError()
                                }
                                return@withTimeout
                            }
                            
                            ensureActive()
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_DECODE_SUCCESS,
                                mapOf("bitmapSize" to "${bitmap.width}x${bitmap.height}")
                            )
                            
                            val rotateStartTime = System.currentTimeMillis()
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_ROTATE_START,
                                mapOf(
                                    "rotationDegrees" to rotationDegrees,
                                    "inputSize" to "${bitmap.width}x${bitmap.height}"
                                )
                            )
                            
                            rotatedBitmap = rotateBitmapSafe(bitmap, rotationDegrees)
                            
                            if (rotatedBitmap == null) {
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                    null,
                                    mapOf(
                                        "reason" to "图片旋转失败",
                                        "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                                    onError()
                                }
                                return@withTimeout
                            }
                            
                            val rotateTime = System.currentTimeMillis() - rotateStartTime
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_ROTATE_START,
                                mapOf(
                                    "step" to "旋转完成",
                                    "rotateTimeMs" to rotateTime,
                                    "outputSize" to "${rotatedBitmap.width}x${rotatedBitmap.height}"
                                )
                            )
                            
                            if (isFrontCamera) {
                                val flipStartTime = System.currentTimeMillis()
                                CameraEventTracker.trackEvent(
                                    CameraEventTracker.EventType.IMAGE_ROTATE_START,
                                    mapOf("step" to "开始水平翻转(前置摄像头)")
                                )
                                
                                val flippedBitmap = flipBitmapHorizontallySafe(rotatedBitmap)
                                
                                if (flippedBitmap == null) {
                                    CameraEventTracker.trackError(
                                        CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                        null,
                                        mapOf("reason" to "图片翻转失败")
                                    )
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "图片处理失败，请重试", Toast.LENGTH_SHORT).show()
                                        onError()
                                    }
                                    return@withTimeout
                                }
                                
                                if (rotatedBitmap != flippedBitmap) {
                                    rotatedBitmap.recycle()
                                }
                                rotatedBitmap = flippedBitmap
                                
                                val flipTime = System.currentTimeMillis() - flipStartTime
                                CameraEventTracker.trackEvent(
                                    CameraEventTracker.EventType.IMAGE_ROTATE_START,
                                    mapOf(
                                        "step" to "水平翻转完成",
                                        "flipTimeMs" to flipTime
                                    )
                                )
                            }
                            
                            if (bitmap != rotatedBitmap) {
                                bitmap.recycle()
                                bitmap = null
                            }
                            
                            ensureActive()
                            val watermarkStartTime = System.currentTimeMillis()
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_WATERMARK_START,
                                mapOf(
                                    "step" to "开始合成水印",
                                    "photoSize" to "${rotatedBitmap.width}x${rotatedBitmap.height}",
                                    "watermarkSize" to "${watermarkBitmap.width}x${watermarkBitmap.height}"
                                )
                            )
                            
                            watermarkedBitmap = addWatermarkSafe(rotatedBitmap, watermarkBitmap, startPx, bottomPx)
                            
                            if (watermarkedBitmap == null) {
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                                    null,
                                    mapOf(
                                        "reason" to "水印合成失败",
                                        "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
                                    )
                                )
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "水印处理失败，请重试", Toast.LENGTH_SHORT).show()
                                    onError()
                                }
                                return@withTimeout
                            }
                            
                            val watermarkTime = System.currentTimeMillis() - watermarkStartTime
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_WATERMARK_START,
                                mapOf(
                                    "step" to "水印合成完成",
                                    "watermarkTimeMs" to watermarkTime,
                                    "resultSize" to "${watermarkedBitmap.width}x${watermarkedBitmap.height}"
                                )
                            )
                            
                            rotatedBitmap.recycle()
                            rotatedBitmap = null
                            watermarkBitmap.recycle()

                            ensureActive()
                            val saveStartTime = System.currentTimeMillis()
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_SAVE_START,
                                mapOf(
                                    "imageSize" to "${watermarkedBitmap.width}x${watermarkedBitmap.height}",
                                    "byteCount" to watermarkedBitmap.byteCount
                                )
                            )
                            
                            val photoFile = File(
                                context.cacheDir,
                                "captured_image_${System.currentTimeMillis()}.jpg"
                            )

                            FileOutputStream(photoFile).use { out ->
                                val finalBitmap = watermarkedBitmap ?: return@use
                                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                            
                            val saveTime = System.currentTimeMillis() - saveStartTime
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_SAVE_START,
                                mapOf(
                                    "step" to "保存完成",
                                    "saveTimeMs" to saveTime,
                                    "fileSizeBytes" to photoFile.length(),
                                    "filePath" to photoFile.name
                                )
                            )
                            
                            watermarkedBitmap.recycle()
                            watermarkedBitmap = null
                            
                            val totalTime = System.currentTimeMillis() - captureStartTime
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.CAPTURE_SUCCESS,
                                mapOf(
                                    "totalTimeMs" to totalTime,
                                    "fileSize" to photoFile.length()
                                )
                            )
                            
                            withContext(Dispatchers.Main) {
                                onImageCaptured(photoFile)
                            }
                        }
                    } catch (e: CancellationException) {
                        CameraEventTracker.trackError(
                            CameraEventTracker.EventType.CAPTURE_ERROR,
                            e,
                            mapOf(
                                "reason" to "协程被取消",
                                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
                            )
                        )
                        withContext(Dispatchers.Main) {
                            onError()
                        }
                        throw e
                    } catch (e: TimeoutCancellationException) {
                        CameraEventTracker.trackError(
                            CameraEventTracker.EventType.CAPTURE_TIMEOUT,
                            e,
                            mapOf("elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime))
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "拍照处理超时，请重试", Toast.LENGTH_SHORT).show()
                            onError()
                        }
                    } catch (e: Exception) {
                        CameraEventTracker.trackError(
                            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                            e,
                            mapOf("elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime))
                        )
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                            onError()
                        }
                    } finally {
                        try {
                            bitmap?.recycle()
                            rotatedBitmap?.recycle()
                            watermarkedBitmap?.recycle()
                        } catch (_: Exception) {
                        }
                        try {
                            image.close()
                        } catch (e: Exception) {
                            CameraEventTracker.trackError(
                                CameraEventTracker.EventType.CAPTURE_ERROR,
                                e,
                                mapOf("reason" to "ImageProxy关闭失败")
                            )
                        }
                    }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                CameraEventTracker.trackError(
                    CameraEventTracker.EventType.CAPTURE_ERROR,
                    exc,
                    mapOf("elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime))
                )
                Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                onError()
            }
        }
    )
}

private fun imageProxyToBitmapSafe(image: ImageProxy, context: Context): Bitmap? {
    return try {
        val decodeStartTime = System.currentTimeMillis()
        
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.IMAGE_DECODE_START,
            mapOf("step" to "读取ImageProxy数据")
        )
        
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        
        val bufferReadTime = System.currentTimeMillis() - decodeStartTime
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.IMAGE_DECODE_START,
            mapOf(
                "step" to "Buffer读取完成",
                "bufferReadTimeMs" to bufferReadTime,
                "bytesSize" to bytes.size
            )
        )
        
        if (bytes.isEmpty()) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                null,
                mapOf("reason" to "图片数据为空")
            )
            return null
        }
        
        val isLowEnd = isLowEndDevice(context)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if (isLowEnd) {
                Bitmap.Config.RGB_565
            } else {
                Bitmap.Config.ARGB_8888
            }
            inMutable = false
            if (isLowEnd && bytes.size > 5 * 1024 * 1024) {
                inSampleSize = 2
            }
        }
        
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.IMAGE_DECODE_START,
            mapOf(
                "step" to "开始BitmapFactory.decodeByteArray",
                "isLowEnd" to isLowEnd,
                "config" to options.inPreferredConfig.toString(),
                "inSampleSize" to options.inSampleSize
            )
        )
        
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        
        val totalDecodeTime = System.currentTimeMillis() - decodeStartTime
        
        if (bitmap == null) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                null,
                mapOf(
                    "reason" to "BitmapFactory.decodeByteArray返回null",
                    "bytesSize" to bytes.size,
                    "decodeTimeMs" to totalDecodeTime
                )
            )
            return null
        }
        
        CameraEventTracker.trackEvent(
            CameraEventTracker.EventType.IMAGE_DECODE_START,
            mapOf(
                "step" to "解码完成",
                "totalDecodeTimeMs" to totalDecodeTime,
                "bitmapWidth" to bitmap.width,
                "bitmapHeight" to bitmap.height,
                "bitmapByteCount" to bitmap.byteCount
            )
        )
        
        bitmap
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during image decode: ${e.message}", e),
            mapOf(
                "reason" to "内存不足",
                "maxMemory" to "${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB",
                "freeMemory" to "${Runtime.getRuntime().freeMemory() / 1024 / 1024}MB"
            )
        )
        System.gc()
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "图片解码异常: ${e.javaClass.simpleName}")
        )
        null
    }
}

private fun rotateBitmapSafe(bitmap: Bitmap, degrees: Float): Bitmap? {
    if (degrees == 0f) return bitmap
    return try {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } catch (e: OutOfMemoryError) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            RuntimeException("OOM during rotate: ${e.message}", e),
            mapOf(
                "reason" to "旋转时内存不足",
                "bitmapSize" to "${bitmap.width}x${bitmap.height}",
                "degrees" to degrees
            )
        )
        System.gc()
        null
    } catch (e: Exception) {
        CameraEventTracker.trackError(
            CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
            e,
            mapOf("reason" to "图片旋转异常: ${e.javaClass.simpleName}")
        )
        null
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
        System.gc()
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
        System.gc()
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
        System.gc()
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

private fun isLowEndDevice(context: Context): Boolean {
    val activityManager = context.getSystemService<ActivityManager>()
    return activityManager?.isLowRamDevice == true ||
           Runtime.getRuntime().maxMemory() < 256 * 1024 * 1024
}