package com.ytone.longcare.features.facecapture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

        FaceCaptureCameraLayout(
            uiState = uiState,
            cameraController = cameraController,
            onNavigateBack = onNavigateBack,
            onClearAllFaces = viewModel::clearAllFaces,
            onSelectFace = viewModel::selectFace,
            onRemoveFace = viewModel::removeFace,
            onConfirmSelectedFace = {
                uiState.selectedFace?.let { bitmap -> onFaceSelected(bitmap) }
            },
            onCancelSelection = viewModel::cancelSelection,
            previewBitmap = previewBitmap,
            onPreviewBitmapChange = { previewBitmap = it },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // 权限被拒绝的UI
        PermissionDeniedScreen(
            onRequestPermission = { launcher.launch(Manifest.permission.CAMERA) },
            onNavigateBack = onNavigateBack
        )
    }
}
