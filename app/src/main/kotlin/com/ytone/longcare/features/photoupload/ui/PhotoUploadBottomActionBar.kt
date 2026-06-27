package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.features.photoupload.api.PhotoUploadActions
import com.ytone.longcare.features.photoupload.viewmodel.PhotoProcessingViewModel
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.ui.components.BottomSafeActionContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun PhotoUploadBottomActionBar(
    buttonText: String,
    isUploading: Boolean,
    enabled: Boolean,
    useMockData: Boolean,
    viewModel: PhotoProcessingViewModel,
    actions: PhotoUploadActions,
    scope: CoroutineScope,
) {
    Surface(modifier = Modifier.fillMaxWidth()) {
        BottomSafeActionContainer(horizontalPadding = 24.dp) {
            ConfirmAndNextButton(
                text = buttonText,
                enabled = enabled,
                isLoading = isUploading,
                onClick = singleClick {
                    com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: Confirm Button Clicked")
                    scope.launch {
                        handleConfirmUpload(
                            useMockData = useMockData,
                            viewModel = viewModel,
                            actions = actions,
                        )
                    }
                },
            )
        }
    }
}

private suspend fun handleConfirmUpload(
    useMockData: Boolean,
    viewModel: PhotoProcessingViewModel,
    actions: PhotoUploadActions,
) {
    if (useMockData) {
        val currentTasks = viewModel.imageTasks.value
        actions.onPublishPhotoUploadResultAndNavigateBack(currentTasks.toSuccessfulTaskTypeMap())
        com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: Mock Success -> navigateBack")
        return
    }

    try {
        val uploadResult = viewModel.uploadSuccessfulImagesToCloud()
        uploadResult.fold(
            onSuccess = { cloudUrlsMap ->
                val currentTasks = viewModel.imageTasks.value
                val imageTasksMap = cloudUrlsMap.toTaskMap(currentTasks)
                actions.onPublishPhotoUploadResultAndNavigateBack(imageTasksMap)
                com.ytone.longcare.common.utils.KLogger.w("NavigationDebug", "PhotoUploadScreen: Upload Success -> navigateBack")
            },
            onFailure = { error ->
                viewModel.showToast("图片上传失败: ${error.message ?: "请检查网络后重试"}")
            },
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        viewModel.showToast("上传过程中发生错误: ${e.message ?: "请稍后重试"}")
    }
}

private fun List<ImageTask>.toSuccessfulTaskTypeMap(): Map<ImageTaskType, List<ImageTask>> {
    return this
        .filter { it.status == ImageTaskStatus.SUCCESS }
        .groupBy { it.taskType }
}

private fun Map<ImageTaskType, List<String>>.toTaskMap(
    tasks: List<ImageTask>,
): Map<ImageTaskType, List<ImageTask>> {
    return mapValues { (taskType, keys) ->
        keys.mapNotNull { key ->
            tasks.find { task ->
                task.taskType == taskType &&
                    task.key == key &&
                    task.status == ImageTaskStatus.SUCCESS
            }
        }
    }
}
