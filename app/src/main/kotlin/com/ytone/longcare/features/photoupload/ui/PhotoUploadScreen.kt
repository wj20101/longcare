package com.ytone.longcare.features.photoupload.ui

import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.common.utils.CustomBackHandler
import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.rememberAsyncImagePainter
import com.ytone.longcare.R
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.ServiceHoursTag
import com.ytone.longcare.ui.screen.TagCategory
import androidx.core.net.toUri
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.model.OrderKey

// --- 数据模型 ---
enum class PhotoCategory(val title: String, val tagCategory: TagCategory) {
    BEFORE_CARE("护理前照片", tagCategory = TagCategory.DEFAULT),
    CENTER_CARE("护理中照片", tagCategory = TagCategory.ORANGE),
    AFTER_CARE("护理后照片", tagCategory = TagCategory.BLUE)
}

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoUploadScreen(
    actions: PhotoUploadActions,
    orderKey: OrderKey,
    viewModel: PhotoProcessingViewModel = hiltViewModel(),
    sharedViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    // 统一处理系统返回键，与导航按钮行为一致（返回上一页）
    CustomBackHandler(customAction = actions.onNavigateBack)

    // 收集ViewModel状态
    val imageTasks by viewModel.imageTasks.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val currentTaskType by viewModel.currentTaskType.collectAsStateWithLifecycle()

    PhotoUploadScreenEffects(
        actions = actions,
        orderKey = orderKey,
        viewModel = viewModel,
        sharedViewModel = sharedViewModel,
        currentTaskType = currentTaskType
    )
    
    val cameraResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                scope.launch {
                    currentTaskType?.let { taskType ->
                        val watermarkData = viewModel.generateWatermarkData(
                            taskType = taskType,
                            address = sharedViewModel.getUserAddress(orderKey),
                            orderId = orderKey.orderId
                        )
                        actions.onNavigateToCamera(watermarkData)
                    }
                }
            } else {
                viewModel.showToast("需要相机权限才能拍照")
            }
        }
    )

    // 根据任务类型获取不同分类的任务
    val beforeCareTasks = imageTasks.filter { it.taskType == ImageTaskType.BEFORE_CARE }
    val centerCareTasks = imageTasks.filter { it.taskType == ImageTaskType.CENTER_CARE }
    val afterCareTasks = imageTasks.filter { it.taskType == ImageTaskType.AFTER_CARE }

    // 检查三个分类是否都有成功上传的图片
    val hasBeforeCareSuccess = beforeCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasCenterCareSuccess = centerCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasAfterCareSuccess = afterCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasCategoriesHaveImages = hasBeforeCareSuccess || hasCenterCareSuccess || hasAfterCareSuccess

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.photo_upload_title), fontWeight = FontWeight.Bold
                    )
                }, navigationIcon = {
                    IconButton(onClick = singleClick {
                        com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: Back Button Clicked -> navigateBack")
                        actions.onNavigateBack()
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                            tint = Color.White
                        )
                    }
                }, colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }, containerColor = Color.Transparent, bottomBar = {
            PhotoUploadBottomActionBar(
                buttonText = if (isUploading) "上传中..." else stringResource(R.string.photo_upload_confirm_and_next),
                isUploading = isUploading,
                enabled = hasCategoriesHaveImages && !isUploading,
                viewModel = viewModel,
                actions = actions,
                scope = scope
            )
        }) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues) // 应用来自Scaffold的padding (包括了底部按钮的空间)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(
                    top = 20.dp,
                    bottom = 24.dp // 增加底部间距，确保内容不会太贴近底部按钮
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp) // 添加统一的垂直间距
            ) {
                item {
                    Text(
                        text = stringResource(R.string.photo_upload_description),
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.9f),
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.BEFORE_CARE,
                        tasks = beforeCareTasks,
                        isUploading = isUploading,
                        onAddPhoto = {
                            viewModel.setCurrentTaskType(ImageTaskType.BEFORE_CARE)
                            cameraResultLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onRetryTask = { taskId -> viewModel.retryTask(taskId) },
                        onRemoveTask = { taskId -> viewModel.removeTask(taskId) })
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.CENTER_CARE,
                        tasks = centerCareTasks,
                        isUploading = isUploading,
                        onAddPhoto = {
                            viewModel.setCurrentTaskType(ImageTaskType.CENTER_CARE)
                            cameraResultLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onRetryTask = { taskId -> viewModel.retryTask(taskId) },
                        onRemoveTask = { taskId -> viewModel.removeTask(taskId) })
                    Spacer(modifier = Modifier.height(16.dp))
                }

                item {
                    PhotoUploadSection(
                        category = PhotoCategory.AFTER_CARE,
                        tasks = afterCareTasks,
                        isUploading = isUploading,
                        onAddPhoto = {
                            viewModel.setCurrentTaskType(ImageTaskType.AFTER_CARE)
                            cameraResultLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onRetryTask = { taskId -> viewModel.retryTask(taskId) },
                        onRemoveTask = { taskId -> viewModel.removeTask(taskId) })
                }
                
                // Mock 按钮区域（仅在 Debug 模式下显示）
                if (BuildConfig.USE_MOCK_DATA) {
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        PhotoUploadMockDebugToolsCard(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoUploadSection(
    category: PhotoCategory,
    tasks: List<ImageTask>,
    isUploading: Boolean,
    onAddPhoto: () -> Unit,
    onRetryTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {

    Box(modifier = modifier) {
        // 图片列表背景卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp),
                modifier = Modifier
                    .padding(
                        start = 20.dp, top = 20.dp, end = 20.dp, bottom = 18.dp
                    )
                    .heightIn(max = 300.dp), // 限制最大高度避免无限约束
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 添加照片按钮作为第一个item
                item {
                    AddPhotoButton(
                        onClick = onAddPhoto, enabled = !isUploading
                    )
                }
                // 后续的图片任务items
                items(tasks) { task ->
                    ImageTaskItem(
                        task = task,
                        onRetry = { onRetryTask(task.id) },
                        onRemove = { onRemoveTask(task.id) },
                        isUploading = isUploading
                    )
                }
            }
        }

        ServiceHoursTag(tagText = category.title, tagCategory = category.tagCategory)

    }
}

@Composable
fun AddPhotoButton(
    onClick: () -> Unit, enabled: Boolean = true
) {
    val lineColor = if (enabled) Color(0xFF2C87FE) else Color.Gray
    val alpha = if (enabled) 1f else 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, color = lineColor, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = singleClick(onClick = onClick))
            .padding(8.dp)
            .graphicsLayer(alpha = alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.photo_upload_add_photo_description),
            tint = lineColor,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.photo_upload_add_photo),
            fontSize = 12.sp,
            color = lineColor
        )
    }
}

@Composable
fun ImageTaskItem(
    task: ImageTask, onRetry: () -> Unit, onRemove: () -> Unit, isUploading: Boolean = false
) {
    var showPreview by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
    ) {
        when (task.status) {
            ImageTaskStatus.PROCESSING -> {
                // 处理中状态：显示加载动画
                Box(
                    modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp), color = Color(0xFF2C87FE)
                    )
                }
            }

            ImageTaskStatus.SUCCESS -> {
                // 成功状态：显示处理后的图片
                task.resultUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = "处理完成的图片",
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showPreview = true },
                        contentScale = ContentScale.Crop
                    )
                }
                // 删除按钮
                if (!isUploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(onClick = onRemove), contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "操作图标",
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                    }
                }
            }

            ImageTaskStatus.FAILED -> {
                // 失败状态：显示错误信息和重试按钮
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "重试",
                        tint = Color.Red,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable { onRetry() })
                    Text(
                        text = "点击重试", fontSize = 8.sp, color = Color.Gray, maxLines = 1
                    )
                }
                // 删除按钮
                if (!isUploading) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp)
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .clickable(onClick = onRemove), contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "操作图标",
                            modifier = Modifier.size(12.dp),
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    // 大图预览对话框
    if (showPreview && task.status == ImageTaskStatus.SUCCESS) {
        task.resultUri?.let { uri ->
            ImagePreviewDialog(
                imageUri = uri, onDismiss = { showPreview = false })
        }
    }
}

@Composable
fun ImagePreviewDialog(
    imageUri: Uri, onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current

    // 使用 LocalWindowInfo 获取屏幕尺寸
    val screenWidth = with(density) { windowInfo.containerSize.width.toDp().toPx() }
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp().toPx() }

    // 缩放状态管理
    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f) // 最小缩放限制为1.0

        // 计算新的偏移量，确保图片不会超出屏幕边界
        val maxOffsetX = if (newScale > 1f) (screenWidth * (newScale - 1)) / 2 else 0f
        val maxOffsetY = if (newScale > 1f) (screenHeight * (newScale - 1)) / 2 else 0f

        val newOffset = if (newScale > 1f) {
            Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            // 当缩放等于1时，重置偏移量为0
            Offset.Zero
        }

        scale = newScale
        offset = newOffset
    }

    Dialog(
        onDismissRequest = onDismiss, properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            // 单击退出预览
                            onDismiss()
                        })
                }
                .transformable(state = transformableState), contentAlignment = Alignment.Center) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "预览图片",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Preview
@Composable
fun PhotoUploadSectionPreview() {
    val tasks = listOf(
        ImageTask(
            id = "1",
            originalUri = Uri.EMPTY,
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.SUCCESS,
            resultUri = "content://media/picker/0/com.android.providers.media.photopicker/media/1000000033".toUri()
        ), ImageTask(
            id = "2",
            originalUri = Uri.EMPTY,
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.PROCESSING
        ), ImageTask(
            id = "3",
            originalUri = Uri.EMPTY,
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.FAILED,
            errorMessage = "Upload failed"
        )
    )
    PhotoUploadSection(
        category = PhotoCategory.BEFORE_CARE,
        tasks = tasks,
        isUploading = false,
        onAddPhoto = {},
        onRetryTask = {},
        onRemoveTask = {})
}

@Preview
@Composable
fun AddPhotoButtonPreview() {
    AddPhotoButton(onClick = {})
}

@Preview
@Composable
fun ImageTaskItemPreview() {
    val task = ImageTask(
        id = "1",
        originalUri = Uri.EMPTY,
        taskType = ImageTaskType.BEFORE_CARE,
    )
    ImageTaskItem(task = task, onRetry = {}, onRemove = {})
}

@Preview
@Composable
fun ConfirmAndNextButtonPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        ConfirmAndNextButton(
            text = "Confirm & Next", enabled = true, isLoading = false, onClick = {})
        Spacer(modifier = Modifier.height(16.dp))
        ConfirmAndNextButton(text = "上传中...", enabled = false, isLoading = true, onClick = {})
    }
}
