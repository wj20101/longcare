package com.ytone.longcare.features.identification.ui

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.R
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.UnifiedBackHandler
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.di.IdentificationEntryPoint
import com.ytone.longcare.features.identification.vm.IdentificationState
import com.ytone.longcare.features.identification.vm.IdentificationViewModel
import com.ytone.longcare.navigation.navigateToCamera
import com.ytone.longcare.navigation.navigateToSelectService
import com.ytone.longcare.navigation.navigateToManualFaceCapture
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.theme.bgGradientBrush
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * 身份认证相关常量
 */
private object IdentificationConstants {
    const val SERVICE_PERSON = "服务人员"
    const val ELDER = "老人"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentificationScreen(
    navController: NavController,
    orderInfoRequest: OrderInfoRequestModel,
    sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel(),
    identificationViewModel: IdentificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val faceVerificationStatusManager = EntryPointAccessors.fromApplication(
        context.applicationContext,
        IdentificationEntryPoint::class.java
    ).faceVerificationStatusManager()

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    
    // 统一处理系统返回键，与导航按钮行为一致（返回上一页）
    UnifiedBackHandler(navController = navController)
    // 观察状态
    val identificationState by identificationViewModel.identificationState.collectAsStateWithLifecycle()
    val faceVerificationState by identificationViewModel.faceVerificationState.collectAsStateWithLifecycle()
    val photoUploadState by identificationViewModel.photoUploadState.collectAsStateWithLifecycle()
    val navigateToFaceCapture by identificationViewModel.navigateToFaceCapture.collectAsStateWithLifecycle()
    val faceSetupState by identificationViewModel.faceSetupState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                scope.launch {
                    val watermarkData = identificationViewModel.generateWatermarkData(
                        address = sharedOrderDetailViewModel.getUserAddress(orderInfoRequest),
                        orderId = orderInfoRequest.orderId
                    )
                    navController.navigateToCamera(watermarkData)
                }
            } else {
                identificationViewModel.showToast("需要相机权限才能拍照")
            }
        }
    )

    // 从CameraScreen获取返回的URI
    LaunchedEffect(navController.currentBackStackEntry?.savedStateHandle) {
        navController.currentBackStackEntry?.savedStateHandle?.get<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)?.let { uriString ->
            val uri = uriString.toUri()
            identificationViewModel.processElderPhoto(uri, orderInfoRequest.orderId)
            // 清除数据，避免重复处理
            navController.currentBackStackEntry?.savedStateHandle?.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY)
        }
    }

    val currentVerificationType by identificationViewModel.currentVerificationType.collectAsStateWithLifecycle()
    
    // 预加载订单详情
    LaunchedEffect(orderInfoRequest.orderId) {
        sharedOrderDetailViewModel.getOrderInfo(orderInfoRequest)
    }
    
    // 处理人脸验证结果
    LaunchedEffect(faceVerificationState, currentVerificationType) {
        when (faceVerificationState) {
            is IdentificationViewModel.FaceVerificationState.Success -> {
                // 验证成功，根据验证类型更新身份认证状态
                when (currentVerificationType) {
                    IdentificationViewModel.VerificationType.SERVICE_PERSON -> {
                        identificationViewModel.setServicePersonVerified()
                    }
                    IdentificationViewModel.VerificationType.ELDER -> {
                        identificationViewModel.setElderVerified()
                        // 老人验证成功后，保存人脸验证完成状态
                        if (orderInfoRequest.orderId > 0) {
                            faceVerificationStatusManager.saveFaceVerificationCompleted(orderInfoRequest.orderId)
                        }
                    }
                    null -> { /* 无验证类型，不处理 */ }
                }
            }
            is IdentificationViewModel.FaceVerificationState.Error -> {
                // 验证失败，可以显示错误提示
            }
            is IdentificationViewModel.FaceVerificationState.Cancelled -> {
                // 用户取消验证
            }
            else -> { /* 其他状态不需要处理 */ }
        }
    }

    // 监听导航到人脸采集页面的状态
    LaunchedEffect(navigateToFaceCapture) {
        if (navigateToFaceCapture) {
            navController.navigateToManualFaceCapture()
            identificationViewModel.resetNavigationState()
        }
    }

    // 监听从人脸采集页面返回的结果
    LaunchedEffect(navController.currentBackStackEntry) {
        navController.currentBackStackEntry?.savedStateHandle?.let { savedStateHandle ->
            val imagePath = savedStateHandle.get<String>(NavigationConstants.FACE_IMAGE_PATH_KEY)
            if (imagePath != null) {
                // 处理捕获的人脸图片
                identificationViewModel.handleFaceCaptureResult(context, imagePath)
                // 清除保存的状态
                savedStateHandle.remove<String>(NavigationConstants.FACE_IMAGE_PATH_KEY)
            }
        }
    }
    
    // 监听拍照上传状态变化
    LaunchedEffect(photoUploadState) {
        when (photoUploadState) {
            is IdentificationViewModel.PhotoUploadState.Success -> {
                // 上传成功，自动跳转到下一步
                navController.navigateToSelectService(orderInfoRequest)
                // 重置状态
                identificationViewModel.resetPhotoUploadState()
            }
            is IdentificationViewModel.PhotoUploadState.Error -> {
                // 上传失败，重置状态
                identificationViewModel.resetPhotoUploadState()
            }
            else -> {}
        }
    }
    
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
                        IconButton(onClick = { navController.popBackStack() }) {
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
                            onClick = { navController.navigateToSelectService(orderInfoRequest) },
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

@Composable
fun IdentificationCard(
    personType: String,
    isVerified: Boolean,
    onVerifyClick: () -> Unit,
    viewModel: IdentificationViewModel,
    faceVerificationState: IdentificationViewModel.FaceVerificationState,
    photoUploadState: IdentificationViewModel.PhotoUploadState = IdentificationViewModel.PhotoUploadState.Initial,
    faceSetupState: IdentificationViewModel.FaceSetupState = IdentificationViewModel.FaceSetupState.Initial
) {
    val identificationState by viewModel.identificationState.collectAsStateWithLifecycle()
    val currentVerificationType by viewModel.currentVerificationType.collectAsStateWithLifecycle()
    
    // 判断当前卡片是否正在进行验证
    val isCurrentlyVerifying = when (personType) {
        IdentificationConstants.SERVICE_PERSON -> currentVerificationType == IdentificationViewModel.VerificationType.SERVICE_PERSON
        IdentificationConstants.ELDER -> currentVerificationType == IdentificationViewModel.VerificationType.ELDER
        else -> false
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 人物头像框
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        width = 1.dp,
                        color = Color.LightGray.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // 头像图片
                if (personType == IdentificationConstants.SERVICE_PERSON) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_service_person),
                        contentDescription = "服务人员头像",
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_elder_person),
                        contentDescription = "老人头像",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (isVerified) {
                    // 已验证状态
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "验证成功",
                            tint = Color(0xFF34C759) // 绿色
                        )
                        Text(
                            text = "${personType}识别成功",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF34C759) // 绿色
                        )
                    }
                } else {
                    // 未验证状态，根据人脸验证状态显示不同UI
                    when {
                        // 人脸设置状态处理（仅服务人员）
                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is IdentificationViewModel.FaceSetupState.UploadingImage -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "上传图片中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is IdentificationViewModel.FaceSetupState.UpdatingServer -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "更新服务器...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is IdentificationViewModel.FaceSetupState.UpdatingLocal -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "更新本地数据...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        personType == IdentificationConstants.SERVICE_PERSON && faceSetupState is IdentificationViewModel.FaceSetupState.Error -> {
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "设置失败",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF3B30)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceSetupState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                        isCurrentlyVerifying && faceVerificationState is IdentificationViewModel.FaceVerificationState.Initializing -> {
                            // 初始化中（仅当前卡片正在验证时显示）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "初始化中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        isCurrentlyVerifying && faceVerificationState is IdentificationViewModel.FaceVerificationState.Verifying -> {
                            // 验证中（仅当前卡片正在验证时显示）
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = "${personType}识别中...",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666666)
                                )
                            }
                        }
                        isCurrentlyVerifying && faceVerificationState is IdentificationViewModel.FaceVerificationState.Error -> {
                            // 验证失败（仅当前卡片正在验证时显示）
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "验证失败",
                                    fontSize = 12.sp,
                                    color = Color(0xFFFF3B30)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceVerificationState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("重试", color = Color.White)
                                }
                            }
                        }
                        isCurrentlyVerifying && faceVerificationState is IdentificationViewModel.FaceVerificationState.Cancelled -> {
                            // 用户取消（仅当前卡片正在验证时显示）
                            Column(
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "已取消",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666666)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        viewModel.resetFaceVerificationState()
                                        onVerifyClick()
                                    },
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623)
                                    ),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("重新识别", color = Color.White)
                                }
                            }
                        }
                        else -> {
                            // 默认状态，显示验证按钮
                            val isButtonEnabled = when {
                                personType == IdentificationConstants.SERVICE_PERSON -> true // 服务人员按钮始终可用
                                personType == IdentificationConstants.ELDER && identificationState == IdentificationState.SERVICE_VERIFIED -> true // 老人按钮仅在服务人员已验证时可用
                                else -> false // 其他情况按钮不可用
                            }
                            
                            // 检查是否正在处理拍照上传（仅对老人卡片）
                            val isProcessing = personType == IdentificationConstants.ELDER && (
                                photoUploadState is IdentificationViewModel.PhotoUploadState.Processing ||
                                photoUploadState is IdentificationViewModel.PhotoUploadState.Uploading
                            )
                            
                            if (isProcessing) {
                                // 显示处理状态
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Text(
                                        text = when (photoUploadState) {
                                            is IdentificationViewModel.PhotoUploadState.Processing -> "处理中..."
                                            is IdentificationViewModel.PhotoUploadState.Uploading -> "上传中..."
                                            else -> "处理中..."
                                        },
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            } else {
                                Button(
                                    onClick = onVerifyClick,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5A623) // 橙色
                                    ),
                                    enabled = isButtonEnabled,
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text(
                                        text = if (personType == IdentificationConstants.ELDER) "拍照验证" else "进行${personType}识别",
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
