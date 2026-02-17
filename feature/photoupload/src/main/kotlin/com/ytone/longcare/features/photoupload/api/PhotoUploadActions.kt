package com.ytone.longcare.features.photoupload.api

import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.WatermarkData
import kotlinx.coroutines.flow.StateFlow

data class PhotoUploadActions(
    val onNavigateBack: () -> Unit,
    val onNavigateToCamera: (WatermarkData) -> Unit,
    val onPublishPhotoUploadResultAndNavigateBack: (Map<ImageTaskType, List<ImageTask>>) -> Unit,
    val existingImagesFlow: StateFlow<Map<ImageTaskType, List<ImageTask>>?>,
    val clearExistingImages: () -> Unit,
    val capturedImageUriFlow: StateFlow<String?>,
    val clearCapturedImageUri: () -> Unit
)
