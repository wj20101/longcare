package com.ytone.longcare.features.servicecountdown.api

import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.navigation.OrderNavParams
import kotlinx.coroutines.flow.StateFlow

data class ServiceCountdownActions(
    val onNavigateHomeAndClearStack: () -> Unit,
    val onNavigateToEndServiceSelection: (OrderNavParams, Int, List<Int>) -> Unit,
    val onNavigateToPhotoUpload: (OrderNavParams, Map<ImageTaskType, List<ImageTask>>) -> Unit,
    val photoUploadResultFlow: StateFlow<Map<ImageTaskType, List<ImageTask>>?>,
    val clearPhotoUploadResult: () -> Unit
)
