package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.model.ImageTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhotoUploadTopBar(actions: PhotoUploadActions) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.photo_upload_title),
                fontWeight = FontWeight.Bold
            )
        },
        navigationIcon = {
            IconButton(
                onClick = singleClick {
                    KLogger.w("NavigationDebug", "PhotoUploadScreen: Back Button Clicked -> navigateBack")
                    actions.onNavigateBack()
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
}

@Composable
internal fun PhotoUploadScreenContent(
    paddingValues: PaddingValues,
    beforeCareTasks: List<ImageTask>,
    centerCareTasks: List<ImageTask>,
    afterCareTasks: List<ImageTask>,
    isUploading: Boolean,
    isMockDataEnabled: Boolean,
    onAddBeforeCarePhoto: () -> Unit,
    onAddCenterCarePhoto: () -> Unit,
    onAddAfterCarePhoto: () -> Unit,
    onRetryTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    viewModel: com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                onAddPhoto = onAddBeforeCarePhoto,
                onRetryTask = onRetryTask,
                onRemoveTask = onRemoveTask
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            PhotoUploadSection(
                category = PhotoCategory.CENTER_CARE,
                tasks = centerCareTasks,
                isUploading = isUploading,
                onAddPhoto = onAddCenterCarePhoto,
                onRetryTask = onRetryTask,
                onRemoveTask = onRemoveTask
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            PhotoUploadSection(
                category = PhotoCategory.AFTER_CARE,
                tasks = afterCareTasks,
                isUploading = isUploading,
                onAddPhoto = onAddAfterCarePhoto,
                onRetryTask = onRetryTask,
                onRemoveTask = onRemoveTask
            )
        }

        if (isMockDataEnabled) {
            item {
                Spacer(modifier = Modifier.height(24.dp))
                PhotoUploadMockDebugToolsCard(viewModel = viewModel)
            }
        }
    }
}
