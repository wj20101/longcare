# 技术方案文档：使用Compose实现实时人脸捕获与筛选

## 1. 文档信息

- **项目名称:** 用户头像智能采集模块
- **功能名称:** 从相机预览中捕获高质量人脸照片 (Compose版)
- **文档版本:** V1.1
- **创建日期:** 2025年10月18日
- **作者:** [您的名字]

## 2. 背景与目标
### 2.1. 项目背景
当前应用需要一个功能，允许用户通过前置摄像头方便地采集自己的人脸照片。为了提升用户体验和照片质量，系统应能自动从视频流中筛选出清晰、正向、高质量的人脸快照，并提供给用户进行最终选择。
### 2.2. 功能目标
实时预览: 在Compose UI中提供一个实时的相机预览。
智能检测: 使用ML Kit人脸检测，在预览的每一帧中识别人脸。
质量评估: 定义一套“优值”（高质量）标准，并根据该标准筛选符合条件的视频帧。
人脸截取: 对于符合条件的帧，能够精确地从完整的相机画面中截取出人脸部分的Bitmap。
列表展示: 将截取到的多张高质量人脸照片（例如最多10张）展示在一个可横向滑动的列表中。
用户选择: 允许用户从列表中点击并选择一张最满意的照片。

## 3. 架构设计
本方案采用Jetpack推荐的MVVM架构，并完全基于Compose进行UI构建。
核心组件:
- **CameraX:** 使用camera-compose库中的LifecycleCameraController，它极大地简化了相机的配置和生命周期管理。
- **ML Kit Face Detection:** 作为图像分析的核心，在ImageAnalysis.Analyzer中被调用。
- **FaceCaptureAnalyzer (自定义分析器):** 负责接收相机帧、调用ML Kit、评估人脸质量并执行裁剪。
- **FaceCaptureViewModel:** 负责持有StateFlow<List<Bitmap>>，管理所有已捕获的高质量人脸Bitmap列表和UI状态。
- **Compose UI Layer:**
    - 使用 rememberLauncherForActivityResult 来处理相机权限。
    - 使用 CameraXViewfinder 原生Composable来显示相机预览。
    - 使用LazyRow来横向展示捕获到的人脸Bitmap列表。
    - 使用Box布局将LazyRow和提示信息叠加在CameraXViewfinder之上。

## 4. 详细设计与实现
### 第1步：添加Gradle依赖
在您模块的 build.gradle 文件中，添加以下依赖：
```kotlin
// build.gradle (app模块)

dependencies {
    // ViewModel and Lifecycle for Compose
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4"
    implementation "androidx.lifecycle:lifecycle-runtime-compose:2.9.4"

    // CameraX
    def camerax_version = "1.5.1"
    implementation "androidx.camera:camera-lifecycle:${camerax_version}"
    implementation "androidx.camera:camera-compose:${camerax_version}"

    // ML Kit Face Detection
    implementation 'com.google.mlkit:face-detection:16.1.6'
}
```

### 第2步：创建ViewModel和图像分析器
**FaceCaptureViewModel.kt**
```kotlin
package com.yourpackage.facecapture

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FaceCaptureViewModel : ViewModel() {
    private val _capturedFaces = MutableStateFlow<List<Bitmap>>(emptyList())
    val capturedFaces = _capturedFaces.asStateFlow()

    private var lastCaptureTime = 0L
    private val CAPTURE_INTERVAL = 1000L // 1秒捕获间隔
    private val MAX_FACES = 10 // 最多捕获10张

    fun onFaceCaptured(faceBitmap: Bitmap) {
        val currentTime = System.currentTimeMillis()
        if (_capturedFaces.value.size < MAX_FACES && (currentTime - lastCaptureTime > CAPTURE_INTERVAL)) {
            _capturedFaces.value += faceBitmap
            lastCaptureTime = currentTime
        }
    }
}
```
**FaceCaptureAnalyzer.kt**
```kotlin
package com.yourpackage.facecapture

import android.graphics.*
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.ByteArrayOutputStream

typealias FaceBitmapCallback = (bitmap: Bitmap) -> Unit

class FaceCaptureAnalyzer(private val onFaceCaptured: FaceBitmapCallback) : ImageAnalysis.Analyzer {

    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setMinFaceSize(0.25f)
        .build()
    private val detector = FaceDetection.getClient(detectorOptions)

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    val highQualityFace = faces.firstOrNull { isFaceGoodQuality(it) }
                    highQualityFace?.let {
                        cropFaceFromImage(imageProxy, it.boundingBox)?.let(onFaceCaptured)
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun isFaceGoodQuality(face: Face): Boolean {
        val isHeadStraight = face.headEulerAngleY in -12.0..12.0 && face.headEulerAngleZ in -8.0..8.0
        val areEyesOpen = (face.leftEyeOpenProbability ?: 0f) > 0.8 && (face.rightEyeOpenProbability ?: 0f) > 0.8
        return isHeadStraight && areEyesOpen
    }
    
    private fun cropFaceFromImage(imageProxy: ImageProxy, boundingBox: Rect): Bitmap? {
        val fullBitmap = imageProxy.toBitmap() ?: return null
        
        // 扩展裁剪区域以包含更多上下文（例如头发）
        val scale = 1.2f
        val newWidth = boundingBox.width() * scale
        val newHeight = boundingBox.height() * scale
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()

        val newLeft = (centerX - newWidth / 2).toInt().coerceAtLeast(0)
        val newTop = (centerY - newHeight / 2).toInt().coerceAtLeast(0)
        val finalWidth = newWidth.toInt().coerceAtMost(fullBitmap.width - newLeft)
        val finalHeight = newHeight.toInt().coerceAtMost(fullBitmap.height - newTop)

        return Bitmap.createBitmap(fullBitmap, newLeft, newTop, finalWidth, finalHeight)
    }

    // 将ImageProxy (YUV_420_888格式) 转换为Bitmap的辅助函数
    private fun ImageProxy.toBitmap(): Bitmap? {
        val yBuffer = planes[0].buffer // Y
        val uBuffer = planes[1].buffer // U
        val vBuffer = planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, this.width, this.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }
}
```

### 第3步：构建Compose UI
**FaceCaptureScreen.kt**
```kotlin
package com.yourpackage.facecapture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.camera.compose.CameraXViewfinder

@Composable
fun FaceCaptureScreen(
    viewModel: FaceCaptureViewModel = viewModel(),
    onFaceSelected: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val capturedFaces by viewModel.capturedFaces.collectAsState()

    var hasCamPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasCamPermission = granted
        }
    )
    LaunchedEffect(key1 = true) {
        if (!hasCamPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }


    if (hasCamPermission) {
        val cameraController = remember {
            LifecycleCameraController(context).apply {
                setImageAnalysisAnalyzer(
                    ContextCompat.getMainExecutor(context),
                    FaceCaptureAnalyzer { bitmap ->
                        viewModel.onFaceCaptured(bitmap)
                    }
                )
                cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                setEnabledUseCases(CameraController.IMAGE_ANALYSIS or CameraController.PREVIEW)
            }
        }

        LaunchedEffect(lifecycleOwner) {
            cameraController.bindToLifecycle(lifecycleOwner)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            CameraXViewfinder(
                controller = cameraController,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "请正对摄像头，保持面部光线充足",
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
                CapturedFacesRow(
                    faces = capturedFaces,
                    onFaceClick = onFaceSelected
                )
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("请求相机权限")
            }
        }
    }
}

@Composable
fun CapturedFacesRow(
    faces: List<Bitmap>,
    onFaceClick: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    if (faces.isNotEmpty()) {
        LazyRow(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(faces) { faceBitmap ->
                Image(
                    bitmap = faceBitmap.asImageBitmap(),
                    contentDescription = "捕获到的人脸",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { onFaceClick(faceBitmap) }
                )
            }
        }
    }
}
```

### 第4步：在Activity中组合所有部分
**MainActivity.kt**
```kotlin
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.yourpackage.facecapture.FaceCaptureScreen
import com.yourpackage.ui.theme.YourAppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YourAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FaceCaptureScreen { selectedBitmap ->
                        // TODO: 处理用户最终选择的Bitmap
                        // 例如，跳转到下一个页面并显示这张图片
                    }
                }
            }
        }
    }
}
```

## 5. 风险与考虑
- **性能:** 在每一帧上运行人脸检测是资源密集型操作。`FaceDetectorOptions.PERFORMANCE_MODE_FAST` 和 `ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST` 是关键的性能优化。
- **内存管理:** ViewModel中持有的Bitmap列表会占用大量内存。必须限制列表的最大长度，并确保ViewModel的生命周期正确，以便在不需要时可以被回收。
- **图像格式与旋转:** `ImageProxy.toBitmap()`转换是本方案中的一个复杂点。示例代码中提供了一个适用于常见YUV格式的转换，但在某些设备上可能需要对不同格式（如RGBA_8888）进行适配。
- **UI反馈:** 当前方案的UI提示比较简单。可以进一步在ViewModel中增加一个UiHint状态，并在FaceCaptureAnalyzer中根据人脸姿态（例如“请靠近一点”、“请勿歪头”）进行实时更新，以提供更好的用户引导。
