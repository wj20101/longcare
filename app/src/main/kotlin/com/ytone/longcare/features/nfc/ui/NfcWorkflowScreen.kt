package com.ytone.longcare.features.nfc.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.di.NfcManagerEntryPoint
import com.ytone.longcare.di.NfcLocationEntryPoint
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.features.nfc.vm.NfcWorkflowViewModel
import com.ytone.longcare.navigation.navigateToServiceComplete
import com.ytone.longcare.navigation.navigateToIdentification
import com.ytone.longcare.features.nfc.vm.NfcSignInUiState
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.UnifiedPermissionHelper.openLocationSettings
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
import com.ytone.longcare.common.utils.UnifiedBackHandler
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel


// --- 状态定义 ---
enum class SignInState {
    IDLE,       // 初始/等待状态
    SUCCESS,    // 签到成功
    FAILURE     // 签到失败
}

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcWorkflowScreen(
    navController: NavController,
    orderInfoRequest: OrderInfoRequestModel,
    signInMode: SignInMode,
    endOderInfo: EndOderInfo? = null,
    nfcViewModel: NfcWorkflowViewModel = hiltViewModel(),
    locationTrackingViewModel: LocationTrackingViewModel = hiltViewModel(),
    sharedOrderDetailViewModel: SharedOrderDetailViewModel = hiltViewModel()
) {
    val uiState by nfcViewModel.uiState.collectAsStateWithLifecycle()
    val pendingNfcData by nfcViewModel.pendingNfcData.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity
    
    // 统一处理系统返回键，与导航按钮行为一致
    UnifiedBackHandler(navController = navController)

    // 权限请求启动器
    val permissionLauncher = rememberLocationPermissionLauncher(
        locationTrackingViewModel = locationTrackingViewModel,
        orderId = orderInfoRequest.orderId
    )

    // 检查定位权限和服务的函数
    fun checkLocationPermissionAndStart() {
        UnifiedPermissionHelper.checkLocationPermissionAndStart(context, permissionLauncher)
    }

    // 获取NfcManager实例
    val nfcManager: NfcManager = remember {
        val appContext: Context = context.applicationContext
        EntryPointAccessors.fromApplication(
            appContext,
            NfcManagerEntryPoint::class.java
        ).nfcManager()
    }

    // 获取CompositeLocationProvider实例
    val locationProvider: CompositeLocationProvider = remember {
        val appContext: Context = context.applicationContext
        EntryPointAccessors.fromApplication(
            appContext,
            NfcLocationEntryPoint::class.java
        ).compositeLocationProvider()
    }

    // 获取当前位置的函数（使用高德定位）
    suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            // 检查定位权限
            if (!UnifiedPermissionHelper.hasLocationPermission(context)) {
                // 申请定位权限
                checkLocationPermissionAndStart()
                return Pair("", "")
            }
            
            // 检查定位服务是否开启
            if (!UnifiedPermissionHelper.isLocationServiceEnabled(context)) {
                // 提醒用户开启定位服务
                openLocationSettings(context)
                nfcViewModel.showError("请开启定位服务以获取位置信息")
                return Pair("", "")
            }
            
            val location = locationProvider.getCurrentLocation()
            if (location != null) {
                Pair(location.longitude.toString(), location.latitude.toString())
            } else {
                Pair("", "")
            }
        } catch (e: Exception) {
            Pair("", "")
        }
    }

    // NFC检查和处理
    LaunchedEffect(Unit) {
        if (activity != null) {
            when {
                !NfcUtils.isNfcSupported(context) -> {
                    // 设备不支持NFC，显示错误信息
                    nfcViewModel.showError("设备不支持NFC功能")
                }

                else -> {
                    // 启用NFC功能
                    nfcManager.enableNfcForActivity(activity)
                }
            }
        }
    }

    // 监听NFC事件
    LaunchedEffect(orderInfoRequest, signInMode) {
        nfcViewModel.observeNfcEvents(
            orderInfoRequest = orderInfoRequest,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            onLocationRequest = { getCurrentLocationCoordinates() },
            sharedOrderDetailViewModel = sharedOrderDetailViewModel
        )
    }

    // 管理NFC前台调度的生命周期
    DisposableEffect(activity) {
        onDispose {
            // 禁用NFC功能
            activity?.let { nfcManager.disableNfcForActivity(it) }
        }
    }

    // 签退成功，关闭定位上传
    LaunchedEffect(uiState) {
        if (signInMode == SignInMode.END_ORDER){
            locationTrackingViewModel.onStopClicked()
        }
    }

    // 根据ViewModel状态确定SignInState
    val signInState = when (uiState) {
        is NfcSignInUiState.Loading -> SignInState.IDLE
        is NfcSignInUiState.Success -> SignInState.SUCCESS
        is NfcSignInUiState.Error -> SignInState.FAILURE
        is NfcSignInUiState.Initial -> SignInState.IDLE
    }

    val titleRes = when (signInMode) {
        SignInMode.START_ORDER -> R.string.nfc_sign_in_title
        SignInMode.END_ORDER -> R.string.nfc_sign_out_title
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgGradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(titleRes),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
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
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.nfc_sign_in_prompt),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(32.dp))

                SignInContentCard(signInState = signInState)

                Spacer(modifier = Modifier.weight(1f)) // 将按钮推到底部

                // 根据状态显示不同的底部按钮
                when (signInState) {
                    SignInState.SUCCESS -> {
                        val buttonText = when (signInMode) {
                            SignInMode.START_ORDER -> stringResource(R.string.common_next_step)
                            SignInMode.END_ORDER -> stringResource(R.string.nfc_sign_out_complete_service)
                        }
                        ActionButton(
                            text = buttonText,
                            onClick = {
                                when (signInMode) {
                                    SignInMode.START_ORDER -> {
                                        // 签到成功时开启定位上报任务
                                        checkLocationPermissionAndStart()
                                        // 签到成功后跳转到身份认证页面
                                        navController.navigateToIdentification(orderInfoRequest)
                                    }
                                    SignInMode.END_ORDER -> {
                                        // 签退时停止定位上报任务
                                        locationTrackingViewModel.onStopClicked()
                                        navController.navigateToServiceComplete(orderInfoRequest)
                                    }
                                }
                            }
                        )
                    }

                    SignInState.FAILURE -> ActionButton(
                        text = stringResource(R.string.nfc_sign_in_retry),
                        onClick = {
                            nfcViewModel.resetState()
                            // 重置状态后等待用户重新靠近NFC设备
                        }
                    )

                    SignInState.IDLE -> {
                        // 初始状态显示等待NFC的提示
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.White.copy(alpha = 0.9f)
                            )
                        ) {
                            Text(
                                text = "请将NFC设备靠近手机背面",
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center,
                                color = Color.Black.copy(alpha = 0.7f)
                            )
                        }
                    }
                }


                Spacer(modifier = Modifier.height(32.dp)) // 按钮与屏幕底部的间距
            }
        }
        
        // 定位激活弹窗
        pendingNfcData?.let { data ->
            LocationActivationDialog(
                onConfirm = { nfcViewModel.confirmLocationActivation(data) },
                onCancel = { nfcViewModel.cancelLocationActivation() }
            )
        }
    }
}

@Composable
fun SignInContentCard(signInState: SignInState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(325f / 260f),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态显示区域
            when (signInState) {
                SignInState.SUCCESS -> StatusDisplay(
                    icon = Icons.Default.CheckCircle,
                    text = stringResource(R.string.nfc_sign_in_status_success),
                    iconColor = Color(0xFF34C759) // 绿色
                )

                SignInState.FAILURE -> StatusDisplay(
                    icon = Icons.Default.Error,
                    text = stringResource(R.string.nfc_sign_in_status_failure),
                    iconColor = Color.Red
                )

                SignInState.IDLE -> {
                    // 初始状态，可以留空，或者有一个小的占位符以保持高度一致性
                    Spacer(modifier = Modifier.height(48.dp)) // 与StatusDisplay高度大致匹配
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // NFC 操作示意图
            Image(
                painter = painterResource(id = R.drawable.nfc_interaction_diagram), // 替换为你的示意图资源
                contentDescription = stringResource(R.string.nfc_sign_in_diagram_description),
                modifier = Modifier
                    .padding(start = 48.dp)
                    .size(170.dp),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
fun StatusDisplay(icon: ImageVector, text: String, iconColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.height(48.dp) // 给状态显示一个固定高度
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = iconColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ActionButton(text: String, onClick: () -> Unit) {
    val buttonGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFF5A9BFA), Color(0xFF3A86FF)) // 按钮的渐变色
    )
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(brush = buttonGradient, shape = CircleShape),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent, // 让 Modifier.background 生效
            contentColor = Color.White
        )
    ) {
        Text(text = text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun LocationActivationDialog(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = "激活定位",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = "定位未激活，需激活方可操作，激活后不可改",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF3A86FF)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "确定激活",
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onCancel,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.Gray
                ),
                border = BorderStroke(1.dp, Color.Gray),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "取消",
                    fontSize = 14.sp
                )
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(12.dp)
    )
}


// --- 预览 ---
@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun NfcSignInScreenIdlePreview() {
    MaterialTheme {
        NfcSignInScreenContentForPreview(SignInState.IDLE) { }
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun NfcSignInScreenSuccessPreview() {
    MaterialTheme {
        NfcSignInScreenContentForPreview(SignInState.SUCCESS) { }
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
fun NfcSignInScreenFailurePreview() {
    MaterialTheme {
        NfcSignInScreenContentForPreview(SignInState.FAILURE) { }
    }
}

// 为预览创建的可修改状态的包装版本 (实际应用中状态由NFC回调等更新)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NfcSignInScreenContentForPreview(
    initialState: SignInState,
    onStateChange: (SignInState) -> Unit
) {
    val signInState by remember { mutableStateOf(initialState) }

    val gradientBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFF5A9BFA), Color(0xFFE3F2FD)),
        startY = 0.0f,
        endY = Float.POSITIVE_INFINITY
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.nfc_sign_in_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { /* TODO: 返回操作 */ }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
                                tint = Color.White
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
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.nfc_sign_in_prompt),
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(32.dp))
                SignInContentCard(signInState = signInState)
                Spacer(modifier = Modifier.weight(1f))
                when (signInState) {
                    SignInState.SUCCESS -> ActionButton(
                        text = stringResource(R.string.common_next_step),
                        onClick = { })

                    SignInState.FAILURE -> ActionButton(
                        text = stringResource(R.string.nfc_sign_in_retry),
                        onClick = { onStateChange(SignInState.IDLE) })

                    SignInState.IDLE -> Box(modifier = Modifier.height(50.dp))
                }
                // 模拟状态切换按钮
                Row(
                    Modifier.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { onStateChange(SignInState.IDLE) }) { Text(stringResource(R.string.common_idle)) }
                    Button(onClick = { onStateChange(SignInState.SUCCESS) }) { Text(stringResource(R.string.common_success)) }
                    Button(onClick = { onStateChange(SignInState.FAILURE) }) { Text(stringResource(R.string.common_failure)) }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Preview(showBackground = true, device = "id:pixel_6")
@Composable
private fun LocationActivationDialogPreview() {
    MaterialTheme {
        LocationActivationDialog(onConfirm = {}, onCancel = {})
    }
}