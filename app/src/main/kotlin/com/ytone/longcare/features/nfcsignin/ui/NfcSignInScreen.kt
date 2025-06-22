package com.ytone.longcare.features.nfcsignin.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dagger.hilt.android.EntryPointAccessors
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcManagerEntryPoint
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.features.nfcsignin.model.EndOderInfo
import com.ytone.longcare.features.nfcsignin.vm.NfcSignInViewModel
import com.ytone.longcare.navigation.navigateToSelectService
import com.ytone.longcare.features.nfcsignin.vm.NfcSignInUiState
import com.ytone.longcare.theme.bgGradientBrush

enum class SignInMode {
    START_ORDER,
    END_ORDER
}

// --- 状态定义 ---
enum class SignInState {
    IDLE,       // 初始/等待状态
    SUCCESS,    // 签到成功
    FAILURE     // 签到失败
}

// --- 主屏幕入口 ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcSignInScreen(
    navController: NavController,
    orderId: Long,
    signInMode: SignInMode, // 新增参数，用于区分开始/结束订单
    endOderInfo: EndOderInfo?,
    viewModel: NfcSignInViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    // 获取NfcManager实例
    val nfcManager: NfcManager = remember {
        val appContext: Context = context.applicationContext
        EntryPointAccessors.fromApplication(
            appContext,
            NfcManagerEntryPoint::class.java
        ).nfcManager()
    }

    // NFC检查和处理
    LaunchedEffect(Unit) {
        if (activity != null) {
            when {
                !NfcUtils.isNfcSupported(context) -> {
                    // 设备不支持NFC，显示错误信息
                    viewModel.showError("设备不支持NFC功能")
                }

                else -> {
                    // 启用NFC功能
                    nfcManager.enableNfcForActivity(activity)
                }
            }
        }
    }

    // 监听NFC事件
    LaunchedEffect(orderId, signInMode) {
        viewModel.observeNfcEvents(orderId, signInMode, endOderInfo)
    }

    // 管理NFC前台调度的生命周期
    DisposableEffect(activity) {
        onDispose {
            // 禁用NFC功能
            activity?.let { nfcManager.disableNfcForActivity(it) }
        }
    }

    // 根据ViewModel状态确定SignInState
    val signInState = when (uiState) {
        is NfcSignInUiState.Loading -> SignInState.IDLE
        is NfcSignInUiState.Success -> SignInState.SUCCESS
        is NfcSignInUiState.Error -> SignInState.FAILURE
        is NfcSignInUiState.Initial -> SignInState.IDLE
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
                            stringResource(R.string.nfc_sign_in_title),
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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
                    SignInState.SUCCESS -> ActionButton(
                        text = stringResource(R.string.common_next_step),
                        onClick = { navController.navigateToSelectService(orderId) }
                    )

                    SignInState.FAILURE -> ActionButton(
                        text = stringResource(R.string.nfc_sign_in_retry),
                        onClick = {
                            viewModel.resetState()
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
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
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