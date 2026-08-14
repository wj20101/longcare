package com.ytone.longcare.features.facecapture

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

/**
 * 人脸捕获主界面
 * 使用最新的CameraX Compose组件和现代化UI设计
 *
 * @param onFaceCaptured 相机采集到第一张合格人脸后的回调
 * @param onNavigateBack 返回导航回调
 * @param viewModel ViewModel实例
 */
@Composable
fun FaceCaptureScreen(
    onFaceCaptured: (Bitmap) -> Unit,
    onNavigateBack: () -> Unit = {},
    title: String = "人脸捕获",
    resetToken: Int = 0,
    viewModel: FaceCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCameraPurposeNotice by remember { mutableStateOf(false) }
    var isPreviewStreaming by remember { mutableStateOf(false) }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var cameraRetryToken by rememberSaveable { mutableStateOf(0) }

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
            cameraPermissionRequested = true
            hasCamPermission = granted
        }
    )

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCamPermission = context.hasCameraPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasCamPermission) {
        val analyzerScope = rememberCoroutineScope()
        val analysisExecutor = remember(cameraRetryToken) {
            Executors.newSingleThreadExecutor()
        }
        val analyzer = remember(viewModel, analyzerScope, cameraRetryToken) {
            FaceCaptureAnalyzer(
                onFaceCaptured = { bitmap, quality ->
                    viewModel.onFaceCaptured(bitmap, quality)
                },
                onHintChanged = { hint ->
                    viewModel.updateUserHint(hint)
                },
                onFaceDetectionChanged = { snapshot ->
                    viewModel.updateFaceDetectionState(snapshot)
                },
                coroutineScope = analyzerScope
            )
        }
        var cameraStartupFailed by remember(cameraRetryToken) { mutableStateOf(false) }

        // 创建相机控制器
        val cameraController = remember(context, cameraRetryToken) {
            LifecycleCameraController(context).apply {
                // 优化设置
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                setEnabledUseCases(
                    CameraController.IMAGE_ANALYSIS
                )
            }
        }

        LaunchedEffect(
            uiState.isDetectionEnabled,
            cameraStartupFailed,
            cameraController,
            analyzer,
            analysisExecutor,
        ) {
            if (uiState.isDetectionEnabled && !cameraStartupFailed) {
                analyzer.reset()
                cameraController.setImageAnalysisAnalyzer(analysisExecutor, analyzer)
            } else {
                cameraController.clearImageAnalysisAnalyzer()
            }
        }

        LaunchedEffect(uiState.captureReady, cameraController, hapticFeedback) {
            if (uiState.captureReady) {
                cameraController.clearImageAnalysisAnalyzer()
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                delay(CAPTURE_SUCCESS_FEEDBACK_MILLIS)
                viewModel.takeCapturedFace()?.let(onFaceCaptured)
            }
        }

        DisposableEffect(cameraController, analyzer, analysisExecutor) {
            onDispose {
                analyzer.release()
                cameraController.clearImageAnalysisAnalyzer()
                cameraController.unbind()
                analysisExecutor.shutdown()
            }
        }

        // CameraX 初始化和绑定都可能失败；失败时保留可返回、可重试的页面状态。
        LaunchedEffect(lifecycleOwner, cameraController, cameraRetryToken) {
            try {
                cameraController.awaitCameraInitialization()
                val cameraSelectors = availableCameraSelectors { selector ->
                    cameraController.hasCamera(selector)
                }
                if (cameraSelectors.isEmpty()) {
                    DiagnosticEventTracker.trackError(
                        category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                        event = "camera_unavailable",
                        description = "人脸采集未检测到可用相机",
                    )
                    analyzer.release()
                    cameraController.clearImageAnalysisAnalyzer()
                    analysisExecutor.shutdown()
                    cameraStartupFailed = true
                    isPreviewStreaming = false
                    return@LaunchedEffect
                }

                var lastBindingFailure: Exception? = null
                var isBound = false
                for (selector in cameraSelectors) {
                    try {
                        cameraController.cameraSelector = selector
                        cameraController.bindToLifecycle(lifecycleOwner)
                        isBound = true
                        break
                    } catch (exception: Exception) {
                        lastBindingFailure = exception
                        runCatching { cameraController.unbind() }
                    }
                }

                if (!isBound) {
                    throw lastBindingFailure
                        ?: IllegalStateException("No camera could be bound for face capture")
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                com.ytone.longcare.common.utils.KLogger.e(
                    "FaceCaptureScreen",
                    "人脸采集相机启动失败: ${exception.message}",
                    exception,
                )
                DiagnosticEventTracker.trackError(
                    category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                    event = "camera_startup_failure",
                    description = "人脸采集相机启动失败",
                    throwable = exception,
                )
                analyzer.release()
                cameraController.clearImageAnalysisAnalyzer()
                runCatching { cameraController.unbind() }
                analysisExecutor.shutdown()
                cameraStartupFailed = true
                isPreviewStreaming = false
            }
        }

        LaunchedEffect(lifecycleOwner, resetToken, viewModel, isPreviewStreaming) {
            if (!isPreviewStreaming) {
                viewModel.resetCapture()
                return@LaunchedEffect
            }

            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.startPreparationCountdown()
                try {
                    awaitCancellation()
                } finally {
                    viewModel.resetCapture()
                }
            }
        }

        if (cameraStartupFailed) {
            CameraUnavailableScreen(
                onRetry = {
                    isPreviewStreaming = false
                    cameraRetryToken += 1
                },
                onNavigateBack = onNavigateBack,
            )
        } else {
            FaceCaptureCameraLayout(
                uiState = uiState,
                cameraController = cameraController,
                title = title,
                onNavigateBack = onNavigateBack,
                onPreviewStreamStateChanged = { streaming ->
                    isPreviewStreaming = streaming
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    } else {
        val activity = remember(context) { context.findActivity() }
        val shouldShowRationale = activity?.let {
            ActivityCompat.shouldShowRequestPermissionRationale(
                it,
                Manifest.permission.CAMERA,
            )
        } ?: false
        val shouldOpenSettings = CameraPermissionRecoveryPolicy.shouldOpenSettings(
            wasRequested = cameraPermissionRequested,
            shouldShowRationale = shouldShowRationale,
        )

        PermissionDeniedScreen(
            showSettingsAction = shouldOpenSettings,
            onPermissionAction = {
                if (shouldOpenSettings) {
                    context.openApplicationSettings()
                } else {
                    showCameraPurposeNotice = true
                }
            },
            onNavigateBack = onNavigateBack
        )
    }

    if (showCameraPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice("拍摄和识别人脸照片"),
            onConfirm = {
                showCameraPurposeNotice = false
                cameraPermissionRequested = true
                launcher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPurposeNotice = false }
        )
    }
}

internal object CameraPermissionRecoveryPolicy {
    fun shouldOpenSettings(
        wasRequested: Boolean,
        shouldShowRationale: Boolean,
    ): Boolean = wasRequested && !shouldShowRationale
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.openApplicationSettings() {
    val settingsIntent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    if (findActivity() == null) {
        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    startActivity(settingsIntent)
}

private const val CAPTURE_SUCCESS_FEEDBACK_MILLIS = 650L
private const val FACE_CAPTURE_DIAGNOSTIC_CATEGORY = "face_capture"
