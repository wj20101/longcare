package com.ytone.longcare.features.facerecognition.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.CustomBackHandler
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.facerecognition.api.FaceRecognitionGuideActions
import com.ytone.longcare.features.facerecognition.vm.FaceRecognitionViewModel
import com.ytone.longcare.theme.bgGradientBrush
import com.ytone.longcare.model.OrderKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FaceRecognitionGuideScreen(
    actions: FaceRecognitionGuideActions,
    orderKey: OrderKey,
    viewModel: FaceRecognitionViewModel = hiltViewModel()
) {
    // ==========================================================
    // 在这里调用函数，将此页面强制设置为竖屏
    // ==========================================================

    // 统一处理系统返回键，与导航按钮行为一致
    CustomBackHandler(customAction = actions.onNavigateBack)
    val privacyAgreed by viewModel.privacyAgreed.collectAsStateWithLifecycle()

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
                        IconButton(onClick = singleClick { actions.onNavigateBack() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.common_back),
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
                FaceRecognitionGuideBottomBar(
                    privacyAgreed = privacyAgreed,
                    onPrivacyAgreementChange = viewModel::updatePrivacyAgreement,
                    onStartClick = singleClick {
                        viewModel.startFaceRecognition()
                        actions.onNavigateToSelectService(orderKey)
                    }
                )
            },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            FaceRecognitionGuideContent(paddingValues)
        }
    }
}
