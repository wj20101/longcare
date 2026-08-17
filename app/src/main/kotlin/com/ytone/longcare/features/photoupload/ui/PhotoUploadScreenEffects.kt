package com.ytone.longcare.features.photoupload.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.core.net.toUri
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingEvent
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel

@Composable
internal fun PhotoUploadScreenEffects(
    actions: PhotoUploadActions,
    orderKey: OrderKey,
    viewModel: PhotoProcessingViewModel,
    sharedViewModel: SharedOrderDetailViewModel,
    currentTaskType: ImageTaskType?,
    onPhotoLimitReached: (Int) -> Unit,
) {
    val latestTaskType by rememberUpdatedState(currentTaskType)
    val latestOnPhotoLimitReached by rememberUpdatedState(onPhotoLimitReached)

    DisposableEffect(Unit) {
        com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: 🟢 Enter Composition")
        onDispose {
            com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: 🔴 Leave Composition (onDispose)")
        }
    }

    LaunchedEffect(orderKey) {
        viewModel.setOrderKey(orderKey)
        if (sharedViewModel.getCachedOrderInfo(orderKey) == null) {
            sharedViewModel.getOrderInfo(orderKey)
        } else {
            sharedViewModel.getOrderInfo(orderKey, forceRefresh = false)
        }
    }

    LaunchedEffect(actions.existingImagesFlow) {
        actions.existingImagesFlow.collect { existingImages ->
            existingImages?.let {
                viewModel.loadExistingImageTasks(it)
                actions.clearExistingImages()
            }
        }
    }

    LaunchedEffect(actions.capturedImageUriFlow) {
        actions.capturedImageUriFlow.collect { uriString ->
            uriString?.let {
                val uri = it.toUri()
                latestTaskType?.let { taskType ->
                    viewModel.addImagesToProcess(
                        uris = listOf(uri),
                        taskType = taskType,
                        address = sharedViewModel.getUserAddress(orderKey),
                        orderKey = orderKey,
                    )
                }
                actions.clearCapturedImageUri()
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is PhotoProcessingEvent.PhotoLimitReached -> latestOnPhotoLimitReached(event.maxCount)
            }
        }
    }
}
