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
                        // 1. Inflate the layout and get the binding object
                        val binding = WatermarkViewBinding.inflate(
                            LayoutInflater.from(ctx),
                            FrameLayout(ctx),
                            false
                        )
                        // 2. Get the root view
                        val view = binding.root
                        // 3. Store the binding in the view's tag for later access in `update`
                        view.tag = binding
                        // 4. Set the view in the state variable to be used by takePhoto
                        watermarkView = view
                        // 5. Return the root view
                        view
                    },
                    update = { view ->
                        // 6. Retrieve the binding from the tag
                        val binding = view.tag as WatermarkViewBinding
                        // 7. Update the TextViews with the watermark data
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


                // 底部控制栏
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 32.dp, end = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 占位符（保持布局对称）
                    Box(modifier = Modifier.size(56.dp))
                    
                    // 拍照按钮
                    ShutterButton(
                        onClick = {
                            // 记录拍照按钮点击
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.CAPTURE_START,
                                mapOf(
                                    "step" to "拍照按钮点击",
                                    "isCameraSwitching" to isCameraSwitching,
                                    "isCapturing" to isCapturing,
                                    "watermarkViewNull" to (watermarkView == null),
                                    "isFrontCamera" to isFrontCamera
                                )
                            )
                            
                            // 如果相机正在切换中或正在拍照中，不允许拍照
                            if (isCameraSwitching || isCapturing) {
                                CameraEventTracker.trackEvent(
                                    CameraEventTracker.EventType.CAPTURE_START,
                                    mapOf(
                                        "step" to "拍照被阻止",
                                        "reason" to if (isCameraSwitching) "相机切换中" else "正在拍照中"
                                    )
                                )
                                return@ShutterButton
                            }
                            
                            if (watermarkView == null) {
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.CAPTURE_ERROR,
                                    null,
                                    mapOf("reason" to "watermarkView为null")
                                )
                                Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                                return@ShutterButton
                            }
                            
                            val view = watermarkView ?: return@ShutterButton
                            
                            // 检查 watermark view 是否已经正确渲染
                            if (view.width <= 0 || view.height <= 0) {
                                CameraEventTracker.trackError(
                                    CameraEventTracker.EventType.CAPTURE_ERROR,
                                    null,
                                    mapOf(
                                        "reason" to "watermarkView尺寸无效",
                                        "viewWidth" to view.width,
                                        "viewHeight" to view.height
                                    )
                                )
                                Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                                return@ShutterButton
                            }
                            
                            // 记录开始执行拍照
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.CAPTURE_START,
                                mapOf(
                                    "step" to "开始执行拍照",
                                    "isFrontCamera" to isFrontCamera,
                                    "watermarkSize" to "${view.width}x${view.height}"
                                )
                            )
                            
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
                        enabled = !isCameraSwitching && !isCapturing
                    )
                    
                    // 切换摄像头按钮（只在有前置摄像头时显示）
                    if (hasFrontCamera) {
                        CameraSwitchButton(
                            onClick = {
                                // 如果已经在切换中或正在拍照，忽略点击
                                if (isCameraSwitching || isCapturing) return@CameraSwitchButton
                                
                                // 保存当前状态，用于失败时回滚
                                val wasUsingFrontCamera = isFrontCamera
                                
                                // 记录摄像头切换开始
                                CameraEventTracker.trackEvent(
                                    CameraEventTracker.EventType.CAMERA_SWITCH_START,
                                    mapOf("from" to if (wasUsingFrontCamera) "front" else "back")
                                )
                                
                                scope.launch {
                                    try {
                                        // 标记相机正在切换
                                        isCameraSwitching = true
                                        
                                        // 确定目标摄像头
                                        val newSelector = if (wasUsingFrontCamera) {
                                            CameraSelector.DEFAULT_BACK_CAMERA
                                        } else {
                                            CameraSelector.DEFAULT_FRONT_CAMERA
                                        }
                                        
                                        // 验证目标摄像头可用
                                        val targetAvailable = try {
                                            cameraController.hasCamera(newSelector)
                                        } catch (_: Exception) {
                                            false
                                        }
                                        
                                        if (!targetAvailable) {
                                            Toast.makeText(context, "目标摄像头不可用", Toast.LENGTH_SHORT).show()
                                            CameraEventTracker.trackError(
                                                CameraEventTracker.EventType.CAMERA_SWITCH_ERROR,
                                                null,
                                                mapOf("reason" to "目标摄像头不可用")
                                            )
                                            return@launch
                                        }
                                        
                                        // 执行切换
                                        cameraController.cameraSelector = newSelector
                                        
                                        // 切换成功，更新状态
                                        isFrontCamera = !wasUsingFrontCamera
                                        
                                        // 等待相机初始化完成
                                        delay(500)
                                        
                                        // 记录摄像头切换成功
                                        CameraEventTracker.trackEvent(
                                            CameraEventTracker.EventType.CAMERA_SWITCH_SUCCESS,
                                            mapOf("to" to if (isFrontCamera) "front" else "back")
                                        )
                                    } catch (e: Exception) {
                                        // 切换失败，状态保持不变（无需回滚，因为还没更新）
                                        CameraEventTracker.trackError(
                                            CameraEventTracker.EventType.CAMERA_SWITCH_ERROR,
                                            e
                                        )
                                        Toast.makeText(context, "切换摄像头失败", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        // 无论成功与否，都要重置切换标记
                                        isCameraSwitching = false
                                    }
                                }
                            },
                            enabled = !isCameraSwitching && !isCapturing
                        )
                    } else {
                        // 占位符保持布局对称
                        Box(modifier = Modifier.size(56.dp))
                    }
                }
                
                // 拍照中遮罩
                if (isCapturing) {
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

/**
 * 拍照按钮
 * 美化设计：外圈白色边框 + 内圈白色实心圆
 */
@Composable
private fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val borderColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    val backgroundColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    
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
            Icon(
                imageVector = Icons.Filled.Circle,
                contentDescription = "拍照",
                modifier = Modifier.size(56.dp),
                tint = backgroundColor
            )
        }
    }
}

/**
 * 摄像头切换按钮
 */
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
                // 使用 COMPATIBLE 模式提升老旧设备兼容性
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
    
    // 记录水印视图捕获开始
    CameraEventTracker.trackEvent(
        CameraEventTracker.EventType.IMAGE_WATERMARK_START,
        mapOf(
            "step" to "捕获水印视图",
            "viewSize" to "${watermarkView.width}x${watermarkView.height}"
        )
    )
    
    // 先在主线程捕获水印视图的 bitmap（View 必须在主线程访问）
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
                // 记录拍照回调接收
                val callbackTime = System.currentTimeMillis() - captureStartTime
                CameraEventTracker.trackEvent(
                    CameraEventTracker.EventType.CAPTURE_CALLBACK_RECEIVED,
                    mapOf(
                        "callbackTimeMs" to callbackTime,
                        "imageSize" to "${image.width}x${image.height}",
                        "rotationDegrees" to image.imageInfo.rotationDegrees
                    )
                )
                
                // 在后台线程处理图片，避免阻塞UI
                scope.launch(Dispatchers.IO) {
                    var bitmap: Bitmap? = null
                    var rotatedBitmap: Bitmap? = null
                    var watermarkedBitmap: Bitmap? = null
                    
                    try {
                        // 添加超时保护，避免处理时间过长导致卡死
                        withTimeout(15_000L) {
                            // 记录图片解码开始
                            CameraEventTracker.trackEvent(CameraEventTracker.EventType.IMAGE_DECODE_START)
                            
                            // 检查协程是否已取消
                            ensureActive()
                            
                            val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                            
                            // 使用增强的解码函数，包含详细日志和错误处理
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
                            
                            // 检查协程是否已取消
                            ensureActive()
                            
                            // 记录图片解码成功
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.IMAGE_DECODE_SUCCESS,
                                mapOf("bitmapSize" to "${bitmap.width}x${bitmap.height}")
                            )
                            
                            // 记录图片旋转开始
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
                            
                            // 如果是前置摄像头，进行水平翻转使照片更自然
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
                            
                            // 回收原始 bitmap
                            if (bitmap != rotatedBitmap) {
                                bitmap.recycle()
                                bitmap = null
                            }
                            
                            // 检查协程是否已取消
                            ensureActive()
                            
                            // 记录添加水印开始
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
                            
                            // 回收中间 bitmap
                            rotatedBitmap.recycle()
                            rotatedBitmap = null
                            watermarkBitmap.recycle()

                            // 检查协程是否已取消
                            ensureActive()
                            
                            // 记录图片保存开始
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
                                // 使用 90% 质量，平衡质量和性能
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
                            
                            // 回收最终 bitmap
                            watermarkedBitmap.recycle()
                            watermarkedBitmap = null
                            
                            // 记录拍照成功
                            val totalTime = System.currentTimeMillis() - captureStartTime
                            CameraEventTracker.trackEvent(
                                CameraEventTracker.EventType.CAPTURE_SUCCESS,
                                mapOf(
                                    "totalTimeMs" to totalTime,
                                    "fileSize" to photoFile.length()
                                )
                            )
                            
                            // 切回主线程回调
                            withContext(Dispatchers.Main) {
                                onImageCaptured(photoFile)
                            }
                        }
                    } catch (e: CancellationException) {
                        // 协程被取消（例如用户按返回键）
                        CameraEventTracker.trackError(
                            CameraEventTracker.EventType.CAPTURE_ERROR,
                            e,
                            mapOf(
                                "reason" to "协程被取消",
                                "elapsedTimeMs" to (System.currentTimeMillis() - captureStartTime)
                            )
                        )
                        // 不显示 Toast，因为用户主动取消
                        withContext(Dispatchers.Main) {
                            onError()
                        }
                        throw e  // 重新抛出让协程正常取消
                    } catch (e: TimeoutCancellationException) {
                        // 记录超时
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
                        // 记录图片处理错误
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
                        // 确保所有 bitmap 都被回收
                        try {
                            bitmap?.recycle()
                            rotatedBitmap?.recycle()
                            watermarkedBitmap?.recycle()
                        } catch (_: Exception) {
                            // 忽略回收错误
                        }
                        // 确保 ImageProxy 总是被关闭
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
                // 记录拍照失败
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


/**
 * 安全的图片解码函数，包含详细日志和异常处理
 * @return 解码后的 Bitmap，如果解码失败返回 null
 */
private fun imageProxyToBitmapSafe(image: ImageProxy, context: Context): Bitmap? {
    return try {
        val decodeStartTime = System.currentTimeMillis()
        
        // 记录开始读取图片数据
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
        
        // 检查数据有效性
        if (bytes.isEmpty()) {
            CameraEventTracker.trackError(
                CameraEventTracker.EventType.IMAGE_PROCESS_ERROR,
                null,
                mapOf("reason" to "图片数据为空")
            )
            return null
        }
        
        // 仅低端设备使用 RGB_565 减少内存，正常设备保持高质量
        val isLowEnd = isLowEndDevice(context)
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = if (isLowEnd) {
                Bitmap.Config.RGB_565  // 低端设备：内存减半
            } else {
                Bitmap.Config.ARGB_8888  // 正常设备：保持高质量
            }
            inMutable = false
            // 对于某些设备，尝试使用 inSampleSize 降低内存压力
            if (isLowEnd && bytes.size > 5 * 1024 * 1024) {
                inSampleSize = 2  // 如果图片大于5MB且是低端设备，缩小一半
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
        // 尝试触发 GC 后重试
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

/**
 * 安全的图片旋转函数，包含异常处理
 * @return 旋转后的 Bitmap，如果失败返回 null
 */
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

/**
 * 安全的图片水平翻转函数，包含异常处理
 * @return 翻转后的 Bitmap，如果失败返回 null
 */
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

/**
 * 安全的视图转Bitmap函数，包含异常处理
 * @return 视图的 Bitmap，如果失败返回 null
 */
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

/**
 * 安全的添加水印函数，包含异常处理
 * @return 添加水印后的 Bitmap，如果失败返回 null
 */
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

/**
 * 检测是否为低端设备
 * 低端设备标准：系统标记为低内存设备，或可用堆内存小于 256MB
 */
private fun isLowEndDevice(context: Context): Boolean {
    val activityManager = context.getSystemService<ActivityManager>()
    return activityManager?.isLowRamDevice == true ||
           Runtime.getRuntime().maxMemory() < 256 * 1024 * 1024
}