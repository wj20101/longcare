package com.ytone.longcare.features.servicecountdown.api

import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.StateFlow

data class ServiceCountdownActions(
    val onNavigateHomeAndClearStack: () -> Unit,
    val onNavigateToEndServiceSelection: (OrderKey, Int, List<Int>) -> Unit,
    val onNavigateToPhotoUpload: (OrderKey, Map<ImageTaskType, List<ImageTask>>) -> Unit,
    val photoUploadResultFlow: StateFlow<Map<ImageTaskType, List<ImageTask>>?>,
    val clearPhotoUploadResult: () -> Unit
)
