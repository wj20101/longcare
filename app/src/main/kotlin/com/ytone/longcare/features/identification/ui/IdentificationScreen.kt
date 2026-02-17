package com.ytone.longcare.features.identification.ui

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.identification.api.IdentificationActions
import com.ytone.longcare.features.identification.vm.FaceSetupState
import com.ytone.longcare.features.identification.vm.FaceVerificationState
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.features.identification.vm.PhotoUploadState
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.theme.bgGradientBrush
import kotlinx.coroutines.launch
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.ui.rememberOrderInfoRequest
import com.ytone.longcare.common.utils.singleClick

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationScreen(
    actions: IdentificationActions,
    orderKey: OrderKey,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    identificationViewModel: IdentificationViewModel = hiltViewModel()
) {
    // 从订单键构建请求模型
    val orderInfoRequest = rememberOrderInfoRequest(orderKey)

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

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                scope.launch {
                    val watermarkData = identificationViewModel.generateWatermarkData(
                        address = sharedOrderDetailViewModel.getUserAddress(orderKey),
                        request = orderInfoRequest
                    )
                    actions.onNavigateToCamera(watermarkData)
                }
            } else {
                identificationViewModel.showToast("需要相机权限才能拍照")
            }
        }
    )

    val currentVerificationType by identificationViewModel.currentVerificationType.collectAsStateWithLifecycle()
    IdentificationScreenEffects(
        actions = actions,
        orderKey = orderKey,
        orderInfoRequest = orderInfoRequest,
        sharedOrderDetailViewModel = sharedOrderDetailViewModel,
        identificationViewModel = identificationViewModel,
        capturedImageUri = capturedImageUri,
        faceImagePath = faceImagePath,
        faceVerificationState = faceVerificationState,
        currentVerificationType = currentVerificationType,
        photoUploadState = photoUploadState,
        context = context
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("身份认证", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White
                    )
                )
            },
            bottomBar = {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 24.dp, top = 16.dp)
                    ) {
                        // 下一步按钮
                        Button(
                            onClick = singleClick { actions.onNavigateToSelectService(orderKey) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4A90E2),
                                disabledContainerColor = Color(0xFF4A90E2).copy(alpha = 0.5f)
                            ),
                            enabled = identificationState == IdentificationState.ELDER_VERIFIED
                        ) {
                            Text("下一步", fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "请按照要求进行人脸识别",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 服务人员识别卡片
                IdentificationCard(
                    personType = IdentificationConstants.SERVICE_PERSON,
                    isVerified = identificationState.ordinal >= IdentificationState.SERVICE_VERIFIED.ordinal,
                    onVerifyClick = {
                        identificationViewModel.verifyServicePerson(context)
                    },
                    viewModel = identificationViewModel,
                    faceVerificationState = faceVerificationState,
                    faceSetupState = faceSetupState
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 老人识别卡片
                IdentificationCard(
                    personType = IdentificationConstants.ELDER,
                    isVerified = identificationState.ordinal >= IdentificationState.ELDER_VERIFIED.ordinal,
                    onVerifyClick = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    viewModel = identificationViewModel,
                    faceVerificationState = faceVerificationState,
                    photoUploadState = photoUploadState
                )

                // Mock Buttons (Debug Only)
                if (BuildConfig.USE_MOCK_DATA) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { identificationViewModel.mockVerifyServicePerson() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mock: 服务人员验证通过")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { identificationViewModel.mockVerifyElder() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Magenta),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Mock: 老人验证通过")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
