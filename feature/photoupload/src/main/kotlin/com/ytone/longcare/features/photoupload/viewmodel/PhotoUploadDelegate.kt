package com.ytone.longcare.features.photoupload.viewmodel

import android.content.Context
import android.net.Uri
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.User
import com.ytone.longcare.model.WatermarkData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

internal class PhotoUploadDelegate(
    private val applicationContext: Context,
    private val cosRepository: CosRepository,
    private val userSessionRepository: UserSessionRepository,
    private val orderDetailRepository: OrderDetailRepository,
    private val taskQueueDelegate: PhotoTaskQueueDelegate,
) {
    val isUploading = MutableStateFlow(false)

    suspend fun generateWatermarkData(taskType: ImageTaskType, address: String, orderId: Long? = null): WatermarkData {
        val title = when (taskType) {
            ImageTaskType.BEFORE_CARE -> "服务前"
            ImageTaskType.CENTER_CARE -> "服务中"
            ImageTaskType.AFTER_CARE -> "服务后"
        }
        val caregiverName = getCurrentUser()?.userName ?: "未知护工"
        val elderName = if (orderId != null) {
            orderDetailRepository.getCachedOrderInfo(OrderKey(orderId))?.userInfo?.name ?: "未知老人"
        } else {
            "未知老人"
        }
        return WatermarkData(title = title, insuredPerson = elderName, caregiver = caregiverName, address = address)
    }

    suspend fun uploadSuccessfulImagesToCloud(): Result<Map<ImageTaskType, List<String>>> {
        return try {
            isUploading.value = true
            val allTasks = taskQueueDelegate.getTasksSnapshot()
            val successfulTasks = allTasks.filter {
                it.status == ImageTaskStatus.SUCCESS && it.resultUri != null && !it.isUploaded
            }
            if (successfulTasks.isEmpty()) {
                isUploading.value = false
                return Result.success(extractUploadedKeys(allTasks))
            }

            for (task in successfulTasks) {
                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = Uri.parse(task.resultUri),
                    folderType = CosConstants.DEFAULT_FOLDER_TYPE
                )
                val result = cosRepository.uploadFile(uploadParams)
                val uploadedUrl = result.url
                val uploadedKey = result.key
                if (result.success && uploadedUrl != null && uploadedKey != null) {
                    taskQueueDelegate.updateTaskUploadStatus(task.id, uploadedUrl, uploadedKey)
                } else {
                    isUploading.value = false
                    return Result.failure(Exception("上传失败: ${result.errorMessage}"))
                }
            }

            isUploading.value = false
            Result.success(extractUploadedKeys(taskQueueDelegate.getTasksSnapshot()))
        } catch (e: CancellationException) {
            isUploading.value = false
            throw e
        } catch (e: Exception) {
            isUploading.value = false
            Result.failure(e)
        }
    }

    private fun extractUploadedKeys(tasks: List<com.ytone.longcare.model.ImageTask>): Map<ImageTaskType, List<String>> {
        return tasks
            .filter { it.status == ImageTaskStatus.SUCCESS && it.isUploaded && it.key != null }
            .groupBy { it.taskType }
            .mapValues { entry -> entry.value.mapNotNull { it.key } }
    }

    private suspend fun getCurrentUser(): User? {
        return when (val sessionState = userSessionRepository.sessionState.value) {
            is SessionState.LoggedIn -> sessionState.user
            else -> null
        }
    }
}
