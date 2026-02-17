package com.ytone.longcare.features.photoupload.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.core.net.toUri
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
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
) {
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
                currentTaskType?.let { taskType ->
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
}
