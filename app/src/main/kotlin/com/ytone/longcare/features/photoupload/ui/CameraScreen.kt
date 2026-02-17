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
import androidx.camera.core.CameraInfoUnavailableException
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.databinding.WatermarkViewBinding
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.load
import coil3.request.allowHardware
import coil3.request.error
import com.ytone.longcare.R
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService

@Composable
fun CameraScreen(
    actions: CameraActions,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    val context = LocalContext.current

    CameraPermissionGate(
        onPermissionGranted = {
            CameraContent(
                context = context,
                watermarkData = watermarkData,
                viewModel = viewModel,
                onImageCaptured = { file ->
                    val savedUri = Uri.fromFile(file)
                    actions.onImageCaptured(savedUri.toString())
                },
            )
        }
    )
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
    val location by viewModel.location.collectAsStateWithLifecycle()
    val time by viewModel.time.collectAsStateWithLifecycle()
    val logoImg by viewModel.syLogoImg.collectAsStateWithLifecycle()
    
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
                                        } catch (_: CameraInfoUnavailableException) {
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
                                            } catch (_: IllegalStateException) {
                                                // 相机切换中可能暂时取不到 cameraInfo，继续轮询即可。
                                            }
                                        }
                                        
                                    } catch (e: CancellationException) {
                                        throw e
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
