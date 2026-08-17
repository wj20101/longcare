package com.ytone.longcare.features.photoupload.ui

import com.ytone.longcare.common.utils.CustomBackHandler
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.ui.screen.TagCategory
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.model.OrderKey
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice

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
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val currentTaskType by viewModel.currentTaskType.collectAsStateWithLifecycle()
    val photoLimitState by viewModel.photoLimitState.collectAsStateWithLifecycle()
    var showCameraPurposeNotice by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    PhotoUploadScreenEffects(
        actions = actions,
        orderKey = orderKey,
        viewModel = viewModel,
        sharedViewModel = sharedViewModel,
        currentTaskType = currentTaskType,
        onPhotoLimitReached = { maxCount ->
            showMessage(resources.getString(R.string.photo_upload_limit_reached, maxCount))
        },
    )
    
    fun openCameraForTask(taskType: ImageTaskType) {
        scope.launch {
            val watermarkData = viewModel.generateWatermarkData(
                taskType = taskType,
                address = sharedViewModel.getUserAddress(orderKey),
                orderId = orderKey.orderId
            )
            actions.onNavigateToCamera(watermarkData)
        }
    }

    val cameraResultLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                currentTaskType?.let { taskType ->
                    openCameraForTask(taskType)
                }
            } else {
                showMessage("需要相机权限才能拍照")
            }
        }
    )

    val requestCameraForTask: (ImageTaskType) -> Unit = { taskType ->
        when {
            !photoLimitState.isLoaded -> {
                showMessage(resources.getString(R.string.photo_upload_config_loading))
            }
            !viewModel.canAddPhoto(taskType) -> {
                photoLimitState.maxPhotosPerCategory?.let { maxCount ->
                    showMessage(resources.getString(R.string.photo_upload_limit_reached, maxCount))
                }
            }
            else -> {
                viewModel.setCurrentTaskType(taskType)
                if (UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
                    openCameraForTask(taskType)
                } else {
                    showCameraPurposeNotice = true
                }
            }
        }
    }

    // 根据任务类型获取不同分类的任务
    val beforeCareTasks = imageTasks.filter { it.taskType == ImageTaskType.BEFORE_CARE }
    val centerCareTasks = imageTasks.filter { it.taskType == ImageTaskType.CENTER_CARE }
    val afterCareTasks = imageTasks.filter { it.taskType == ImageTaskType.AFTER_CARE }

    // 检查三个分类是否都有成功上传的图片
    val hasBeforeCareSuccess = beforeCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasCenterCareSuccess = centerCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasAfterCareSuccess = afterCareTasks.any { it.status == ImageTaskStatus.SUCCESS }
    val hasCategoriesHaveImages = hasBeforeCareSuccess || hasCenterCareSuccess || hasAfterCareSuccess

    Box(modifier = Modifier.fillMaxSize().background(bgGradientBrush)) {
        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = { PhotoUploadTopBar(actions = actions) },
            containerColor = Color.Transparent,
            bottomBar = {
            PhotoUploadBottomActionBar(
                buttonText = if (isUploading) "上传中..." else stringResource(R.string.photo_upload_confirm_and_next),
                isUploading = isUploading,
                enabled = hasCategoriesHaveImages && !isUploading,
                viewModel = viewModel,
                actions = actions,
                scope = scope,
                onShowMessage = showMessage,
            )
            }
        ) { paddingValues ->
            PhotoUploadScreenContent(
                paddingValues = paddingValues,
                beforeCareTasks = beforeCareTasks,
                centerCareTasks = centerCareTasks,
                afterCareTasks = afterCareTasks,
                isUploading = isUploading,
                isPhotoLimitLoaded = photoLimitState.isLoaded,
                maxPhotosPerCategory = photoLimitState.maxPhotosPerCategory,
                onAddBeforeCarePhoto = {
                    requestCameraForTask(ImageTaskType.BEFORE_CARE)
                },
                onAddCenterCarePhoto = {
                    requestCameraForTask(ImageTaskType.CENTER_CARE)
                },
                onAddAfterCarePhoto = {
                    requestCameraForTask(ImageTaskType.AFTER_CARE)
                },
                onRetryTask = viewModel::retryTask,
                onRemoveTask = viewModel::removeTask
            )
        }
    }

    if (showCameraPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice("拍摄服务照片并上传护理记录"),
            onConfirm = {
                showCameraPurposeNotice = false
                cameraResultLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPurposeNotice = false }
        )
    }
}
