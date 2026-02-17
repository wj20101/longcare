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
