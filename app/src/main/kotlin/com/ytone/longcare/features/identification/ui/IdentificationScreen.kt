package com.ytone.longcare.features.identification.ui

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.cameraPermissionPurposeNotice
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import kotlinx.coroutines.launch
import com.ytone.longcare.model.OrderKey

@Composable
fun IdentificationScreen(
    actions: IdentificationActions,
    orderKey: OrderKey,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    identificationViewModel: IdentificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    // 统一处理系统返回键，与导航按钮行为一致（返回上一页）
    CustomBackHandler(customAction = actions.onNavigateBack)
    // 观察状态
    val identificationState by identificationViewModel.identificationState.collectAsStateWithLifecycle()
    val faceVerificationState by identificationViewModel.faceVerificationState.collectAsStateWithLifecycle()
    val photoUploadState by identificationViewModel.photoUploadState.collectAsStateWithLifecycle()
    val faceSetupState by identificationViewModel.faceSetupState.collectAsStateWithLifecycle()
    val capturedImageUri by actions.capturedImageUriFlow.collectAsStateWithLifecycle()
    val faceImagePath by actions.faceImagePathFlow.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showCameraPurposeNotice by remember { mutableStateOf(false) }
    fun openElderCamera() {
        scope.launch {
            navigateToElderCamera(
                sharedOrderDetailViewModel = sharedOrderDetailViewModel,
                identificationViewModel = identificationViewModel,
                actions = actions,
                orderKey = orderKey
            )
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                openElderCamera()
            } else {
                identificationViewModel.showToast("需要相机权限才能拍照")
            }
        }
    )

    val currentVerificationType by identificationViewModel.currentVerificationType.collectAsStateWithLifecycle()
    IdentificationScreenEffects(
        actions = actions,
        orderKey = orderKey,
        sharedOrderDetailViewModel = sharedOrderDetailViewModel,
        identificationViewModel = identificationViewModel,
        capturedImageUri = capturedImageUri,
        faceImagePath = faceImagePath,
        faceVerificationState = faceVerificationState,
        currentVerificationType = currentVerificationType,
        photoUploadState = photoUploadState,
        context = context
    )

    IdentificationScreenScaffoldContent(
        identificationState = identificationState,
        faceVerificationState = faceVerificationState,
        photoUploadState = photoUploadState,
        faceSetupState = faceSetupState,
        identificationViewModel = identificationViewModel,
        isMockDataEnabled = identificationViewModel.isMockDataEnabled,
        onNavigateBack = actions.onNavigateBack,
        onNavigateToSelectService = { actions.onNavigateToSelectService(orderKey) },
        onVerifyServicePerson = { identificationViewModel.verifyServicePerson(context) },
        onVerifyElder = {
            if (UnifiedPermissionHelper.isCameraPermissionGranted(context)) {
                openElderCamera()
            } else {
                showCameraPurposeNotice = true
            }
        },
        onMockVerifyServicePerson = identificationViewModel::mockVerifyServicePerson,
        onMockVerifyElder = identificationViewModel::mockVerifyElder
    )

    if (showCameraPurposeNotice) {
        PermissionPurposeDialog(
            notice = cameraPermissionPurposeNotice("拍摄老人核验照片"),
            onConfirm = {
                showCameraPurposeNotice = false
                permissionLauncher.launch(Manifest.permission.CAMERA)
            },
            onDismiss = { showCameraPurposeNotice = false }
        )
    }
}

private suspend fun navigateToElderCamera(
    sharedOrderDetailViewModel: SharedOrderDetailViewModel,
    identificationViewModel: IdentificationViewModel,
    actions: IdentificationActions,
    orderKey: OrderKey
) {
    val watermarkData = identificationViewModel.generateWatermarkData(
        address = sharedOrderDetailViewModel.getUserAddress(orderKey),
        orderKey = orderKey
    )
    actions.onNavigateToCamera(watermarkData)
}
