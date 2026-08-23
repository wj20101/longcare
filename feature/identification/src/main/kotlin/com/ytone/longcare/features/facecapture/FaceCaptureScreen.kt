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
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.ytone.longcare.feature.identification.R
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

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
    title: String? = null,
    resetToken: Int = 0,
    viewModel: FaceCaptureViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hapticFeedback = LocalHapticFeedback.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val resolvedTitle = title ?: stringResource(R.string.face_capture_title)
    var showCameraPurposeNotice by remember { mutableStateOf(false) }
    var isPreviewStreaming by remember { mutableStateOf(false) }
    var cameraPermissionRequested by rememberSaveable { mutableStateOf(false) }
    var cameraRetryToken by rememberSaveable { mutableIntStateOf(0) }

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
        val analysisExecutor = remember(cameraRetryToken) {
            Executors.newSingleThreadExecutor()
        }
        val analyzer = remember(viewModel, analysisExecutor, cameraRetryToken) {
            FaceCaptureAnalyzer(
                callbackExecutor = analysisExecutor,
                onCaptureRequested = viewModel::onBlinkVerified,
                onHintChanged = { hint ->
                    viewModel.updateUserHint(hint)
                },
                onFaceDetectionChanged = { snapshot ->
                    viewModel.updateFaceDetectionState(snapshot)
                },
            )
        }
        val capturedFaceProcessor = remember(viewModel, analysisExecutor, cameraRetryToken) {
            CapturedFaceProcessor(
                callbackExecutor = analysisExecutor,
                onFaceProcessed = viewModel::onFaceCaptured,
                onFailure = { message, error ->
                    if (error != null) {
                        DiagnosticEventTracker.trackError(
                            category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                            event = "still_image_process_failure",
                            description = "眨眼完成后的人脸照片处理失败",
                            throwable = error,
                        )
                    }
                    viewModel.onStillCaptureFailed(message)
                },
            )
        }
        var cameraStartupFailed by remember(cameraRetryToken) { mutableStateOf(false) }

        // 创建相机控制器
        val cameraController = remember(context, cameraRetryToken, analyzer, analysisExecutor) {
            LifecycleCameraController(context).apply {
                // 优化设置
                imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                setEnabledUseCases(
                    CameraController.IMAGE_ANALYSIS or CameraController.IMAGE_CAPTURE,
                )
                setImageAnalysisAnalyzer(analysisExecutor, analyzer.imageAnalyzer)
            }
        }

        LaunchedEffect(
            uiState.isDetectionEnabled,
            analyzer,
        ) {
            analyzer.setDetectionEnabled(uiState.isDetectionEnabled)
        }

        LaunchedEffect(
            uiState.isStillCaptureRequested,
            isPreviewStreaming,
            cameraStartupFailed,
            cameraController,
            analysisExecutor,
            capturedFaceProcessor,
        ) {
            if (
                !uiState.isStillCaptureRequested ||
                !isPreviewStreaming ||
                cameraStartupFailed
            ) {
                return@LaunchedEffect
            }

            try {
                cameraController.takePicture(
                    analysisExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            capturedFaceProcessor.process(image)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            DiagnosticEventTracker.trackError(
                                category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                                event = "still_image_capture_failure",
                                description = "眨眼完成后相机拍照失败",
                                throwable = exception,
                            )
                            viewModel.onStillCaptureFailed(FaceCaptureHint.CAPTURE_FAILED)
                        }
                    },
                )
            } catch (error: Exception) {
                DiagnosticEventTracker.trackError(
                    category = FACE_CAPTURE_DIAGNOSTIC_CATEGORY,
                    event = "still_image_capture_start_failure",
                    description = "眨眼完成后无法启动拍照",
                    throwable = error,
                )
                viewModel.onStillCaptureFailed(FaceCaptureHint.CAPTURE_FAILED)
            }
        }

        LaunchedEffect(uiState.captureReady, cameraController, hapticFeedback) {
            if (uiState.captureReady) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                delay(CAPTURE_SUCCESS_FEEDBACK_MILLIS)
                viewModel.takeCapturedFace()?.let(onFaceCaptured)
            }
        }

        DisposableEffect(cameraController, analyzer, capturedFaceProcessor, analysisExecutor) {
            onDispose {
                cameraController.clearImageAnalysisAnalyzer()
                capturedFaceProcessor.release()
                analyzer.release()
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
                    cameraController.clearImageAnalysisAnalyzer()
                    capturedFaceProcessor.release()
                    analyzer.release()
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
                cameraController.clearImageAnalysisAnalyzer()
                capturedFaceProcessor.release()
                analyzer.release()
                runCatching { cameraController.unbind() }
                analysisExecutor.shutdown()
                cameraStartupFailed = true
                isPreviewStreaming = false
            }
        }

        LaunchedEffect(lifecycleOwner, resetToken, cameraRetryToken, viewModel) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                snapshotFlow { isPreviewStreaming }
                    .filter { it }
                    .first()
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
                title = resolvedTitle,
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
            notice = cameraPermissionPurposeNotice(
                stringResource(R.string.face_capture_permission_purpose),
            ),
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
