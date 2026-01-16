package com.ytone.longcare.features.photoupload.ui

import android.Manifest
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.graphics.createBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.load
import coil3.request.allowHardware
import coil3.request.error
import com.ytone.longcare.R

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
            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1920, 1080),
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
    val scope = rememberCoroutineScope()


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
                            // 如果相机正在切换中或正在拍照中，不允许拍照
                            if (isCameraSwitching || isCapturing) return@ShutterButton
                            
                            watermarkView?.let { view ->
                                // 检查 watermark view 是否已经正确渲染
                                if (view.width <= 0 || view.height <= 0) {
                                    Toast.makeText(context, "请稍候，正在准备中...", Toast.LENGTH_SHORT).show()
                                    return@ShutterButton
                                }
                                
                                isCapturing = true
                                viewModel.updateTime()
                                val executor = ContextCompat.getMainExecutor(context)
                                takePhoto(
                                    context = context,
                                    cameraController = cameraController,
                                    executor = executor,
                                    watermarkView = view,
                                    isFrontCamera = isFrontCamera,
                                    onImageCaptured = { file ->
                                        isCapturing = false
                                        onImageCaptured(file)
                                    },
                                    onError = {
                                        isCapturing = false
                                    }
                                )
                            }
                        },
                        enabled = !isCameraSwitching && !isCapturing
                    )
                    
                    // 切换摄像头按钮
                    CameraSwitchButton(
                        onClick = {
                            // 如果已经在切换中，忽略点击
                            if (isCameraSwitching) return@CameraSwitchButton
                            
                            scope.launch {
                                try {
                                    // 标记相机正在切换
                                    isCameraSwitching = true
                                    
                                    val newSelector = if (isFrontCamera) {
                                        CameraSelector.DEFAULT_BACK_CAMERA
                                    } else {
                                        CameraSelector.DEFAULT_FRONT_CAMERA
                                    }
                                    cameraController.cameraSelector = newSelector
                                    isFrontCamera = !isFrontCamera
                                    
                                    // 等待相机初始化完成
                                    delay(500)
                                } catch (e: Exception) {
                                    // 相机不支持切换（例如设备只有一个摄像头，或前置摄像头不支持所需功能）
                                    Toast.makeText(context, "无法切换摄像头", Toast.LENGTH_SHORT).show()
                                } finally {
                                    // 无论成功与否，都要重置标记
                                    isCameraSwitching = false
                                }
                            }
                        },
                        enabled = !isCameraSwitching
                    )
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
    onImageCaptured: (File) -> Unit,
    onError: () -> Unit
) {
    cameraController.takePicture(
        executor,
        object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val bitmap = imageProxyToBitmap(image)
                    val rotationDegrees = image.imageInfo.rotationDegrees.toFloat()
                    var rotatedBitmap = rotateBitmap(bitmap, rotationDegrees)
                    
                    // 如果是前置摄像头，进行水平翻转使照片更自然
                    if (isFrontCamera) {
                        val flippedBitmap = flipBitmapHorizontally(rotatedBitmap)
                        if (rotatedBitmap != flippedBitmap) {
                            rotatedBitmap.recycle()
                        }
                        rotatedBitmap = flippedBitmap
                    }
                    
                    // 回收原始 bitmap
                    if (bitmap != rotatedBitmap) {
                        bitmap.recycle()
                    }
                    
                    val watermarkBitmap = viewToBitmap(watermarkView)
                    val density = context.resources.displayMetrics.density
                    val startPx = (13 * density)
                    val bottomPx = (14 * density)
                    val watermarkedBitmap =
                        addWatermark(rotatedBitmap, watermarkBitmap, startPx, bottomPx)
                    
                    // 回收中间 bitmap
                    rotatedBitmap.recycle()
                    watermarkBitmap.recycle()

                    val photoFile = File(
                        context.cacheDir,
                        "captured_image_${System.currentTimeMillis()}.jpg"
                    )

                    FileOutputStream(photoFile).use { out ->
                        watermarkedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    
                    // 回收最终 bitmap
                    watermarkedBitmap.recycle()
                    
                    onImageCaptured(photoFile)
                } catch (e: Exception) {
                    Toast.makeText(context, "图片处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    onError()
                } finally {
                    image.close()
                }
            }

            override fun onError(exc: ImageCaptureException) {
                Toast.makeText(context, "拍照失败: ${exc.message}", Toast.LENGTH_SHORT).show()
                onError()
            }
        }
    )
}


private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val planeProxy = image.planes[0]
    val buffer: ByteBuffer = planeProxy.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}

private fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
    if (degrees == 0f) return bitmap
    val matrix = Matrix()
    matrix.postRotate(degrees)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun flipBitmapHorizontally(bitmap: Bitmap): Bitmap {
    val matrix = Matrix()
    matrix.preScale(-1f, 1f)
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}

private fun viewToBitmap(view: View): Bitmap {
    val bitmap = createBitmap(view.width, view.height)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}

private fun addWatermark(
    bitmap: Bitmap,
    watermark: Bitmap,
    startPx: Float,
    bottomPx: Float
): Bitmap {
    val result = createBitmap(bitmap.width, bitmap.height)
    val canvas = Canvas(result)
    canvas.drawBitmap(bitmap, 0f, 0f, null)
    canvas.drawBitmap(watermark, startPx, (bitmap.height - watermark.height - bottomPx), null)
    return result
}