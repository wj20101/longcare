package com.ytone.longcare.features.photoupload.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraInfoUnavailableException
import androidx.camera.core.CameraSelector
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.load
import coil3.request.allowHardware
import coil3.request.error
import com.ytone.longcare.R
import com.ytone.longcare.databinding.WatermarkViewBinding
import com.ytone.longcare.features.photoupload.tracker.CameraEventTracker
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.WatermarkData
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun CameraContent(
    context: Context,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel,
    onImageCaptured: (File) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraController = remember {
        LifecycleCameraController(context).apply {
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

    var isFrontCamera by remember { mutableStateOf(false) }
    var isCameraSwitching by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var hasFrontCamera by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(cameraController) {
        var detected = false
        for (attempt in 1..3) {
            delay(200L * attempt)

            detected = try {
                val hasCamera = cameraController.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                if (hasCamera) {
                    true
                } else {
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

    var delayMode by remember { mutableStateOf(DelayMode.OFF) }
    var countdownSeconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val isCountingDown = countdownSeconds > 0

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
                onError = { isCapturing = false }
            )
        } else {
            Toast.makeText(context, "水印准备中，请稍后...", Toast.LENGTH_SHORT).show()
        }
    }

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

                CameraTopToolbar(
                    delayMode = delayMode,
                    enabled = !isCapturing && !isCountingDown,
                    onToggleDelayMode = { delayMode = delayMode.next() }
                )

                CameraBottomControlBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hasFrontCamera = hasFrontCamera,
                    isCameraSwitching = isCameraSwitching,
                    isCapturing = isCapturing,
                    isCountingDown = isCountingDown,
                    onShutterClick = {
                        if (isCountingDown) {
                            countdownSeconds = 0
                        } else if (!isCameraSwitching && !isCapturing) {
                            val view = watermarkView
                            if (view == null || view.width <= 0 || view.height <= 0) {
                                Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                            } else if (delayMode != DelayMode.OFF) {
                                countdownSeconds = delayMode.seconds
                            } else {
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
                                    onError = { isCapturing = false }
                                )
                            }
                        }
                    },
                    onSwitchCameraClick = {
                        if (!isCameraSwitching && !isCapturing) {
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

                                    var waitTime = 0L
                                    val maxWaitTime = 1500L
                                    val checkInterval = 100L
                                    while (waitTime < maxWaitTime) {
                                        delay(checkInterval)
                                        waitTime += checkInterval
                                        try {
                                            if (cameraController.cameraInfo != null) break
                                        } catch (_: IllegalStateException) {
                                            // 相机切换中可能暂时取不到 cameraInfo，继续轮询。
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
                        }
                    }
                )

                CameraCountdownOverlay(
                    countdownSeconds = countdownSeconds,
                    visible = isCountingDown
                )

                CameraCapturingOverlay(visible = isCapturing && !isCountingDown)
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
