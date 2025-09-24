package com.ytone.longcare.features.facerecognition.ui

import android.content.pm.ActivityInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.CompositionLocalProvider
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.features.facerecognition.vm.FaceRecognitionViewModel
import com.ytone.longcare.navigation.navigateToSelectService
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.common.utils.UnifiedBackHandler

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRecognitionGuideScreen(
    navController: NavController,
    orderInfoRequest: OrderInfoRequestModel,
    viewModel: FaceRecognitionViewModel = hiltViewModel()
) {

    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
    
    // 统一处理系统返回键，与导航按钮行为一致
    UnifiedBackHandler(navController = navController)

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
                            stringResource(id = R.string.face_recognition_guide_title),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
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
            containerColor = Color.Transparent,
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.face_recognition_guide_subtitle),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(20.dp))

                // 主要内容卡片
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // 人脸识别指导图片
                    Image(
                        painter = painterResource(id = R.drawable.face_recognition_guide),
                        contentDescription = "人脸识别指导",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(582f / 589f)
                            .padding(16.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // 获取隐私政策同意状态
                val privacyAgreed by viewModel.privacyAgreed.collectAsStateWithLifecycle()

                // 开始人脸识别按钮
                Button(
                    onClick = {
                        // 调用ViewModel的方法开始人脸识别
                        viewModel.startFaceRecognition()
                        // 跳转到选择服务页面
                        navController.navigateToSelectService(orderInfoRequest)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4A90E2), // 蓝色
                        disabledContainerColor = Color(0xFF4A90E2).copy(alpha = 0.5f)
                    ),
                    enabled = privacyAgreed
                ) {
                    Text(
                        stringResource(id = R.string.face_recognition_guide_start_button),
                        fontSize = 16.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 隐私政策提示
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Checkbox(
                        checked = privacyAgreed,
                        onCheckedChange = { viewModel.updatePrivacyAgreement(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF4A90E2),
                            checkmarkColor = Color.White
                        )
                    )
                    Text(
                        text = stringResource(id = R.string.face_recognition_guide_privacy_agreement),
                        fontSize = 12.sp,
                        color = Color(0xFF666666),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FaceRecognitionGuideScreenPreview() {
    val previewViewModel: FaceRecognitionViewModel = hiltViewModel()
    CompositionLocalProvider(LocalViewModelStoreOwner provides PreviewViewModelStoreOwner()) {
        FaceRecognitionGuideScreen(
            navController = rememberNavController(),
            orderInfoRequest = OrderInfoRequestModel(orderId = 1, planId = 0),
            viewModel = previewViewModel
        )
    }
}

/**
 * 用于预览的ViewModelStoreOwner
 */
private class PreviewViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore: ViewModelStore
        get() = viewModelStore
}