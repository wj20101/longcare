package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow

internal class PhotoUploadDelegate(
    private val photoCloudUploader: PhotoCloudUploader,
    private val userSessionRepository: UserSessionRepository,
    private val orderDetailRepository: OrderDetailRepository,
    private val taskQueueDelegate: PhotoTaskQueueDelegate,
    private val userMessages: PhotoUploadMessages,
) {
    val isUploading = MutableStateFlow(false)

    suspend fun generateWatermarkData(taskType: ImageTaskType, address: String, orderId: Long? = null): WatermarkData {
        val title = when (taskType) {
            ImageTaskType.BEFORE_CARE -> userMessages.beforeService
            ImageTaskType.CENTER_CARE -> userMessages.duringService
            ImageTaskType.AFTER_CARE -> userMessages.afterService
        }
        val caregiverName = getCurrentUser()?.userName ?: userMessages.unknownCaregiver
        val elderName = if (orderId != null) {
            orderDetailRepository.getCachedOrderInfo(OrderKey(orderId))?.userInfo?.name
                ?: userMessages.unknownElder
        } else {
            userMessages.unknownElder
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
                try {
                    val uploadedPhoto = photoCloudUploader.upload(Uri.parse(task.resultUri))
                    taskQueueDelegate.updateTaskUploadStatus(
                        taskId = task.id,
                        key = uploadedPhoto.key,
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (uploadError: Exception) {
                    isUploading.value = false
                    val errorMessage = uploadError.message
                    DiagnosticEventTracker.trackError(
                        category = PHOTO_DIAGNOSTIC_CATEGORY,
                        event = "cloud_upload_failure",
                        description = "服务照片上传COS失败",
                        extras = mapOf(
                            "orderId" to taskQueueDelegate.currentOrderKey.value?.orderId,
                            "planId" to taskQueueDelegate.currentOrderKey.value?.planId,
                            "taskType" to task.taskType.name,
                            "taskIdLength" to task.id.length,
                            "uploadedTaskCount" to taskQueueDelegate.getTasksSnapshot().count { it.isUploaded },
                            "pendingTaskCount" to successfulTasks.size,
                            "errorMessage" to errorMessage,
                        ),
                    )
                    return Result.failure(
                        Exception(userMessages.cloudUploadFailed, uploadError),
                    )
                }
            }

            isUploading.value = false
            Result.success(extractUploadedKeys(taskQueueDelegate.getTasksSnapshot()))
        } catch (e: CancellationException) {
            isUploading.value = false
            throw e
        } catch (e: Exception) {
            isUploading.value = false
            DiagnosticEventTracker.trackError(
                category = PHOTO_DIAGNOSTIC_CATEGORY,
                event = "cloud_upload_exception",
                description = "服务照片上传过程异常",
                throwable = e,
                extras = mapOf(
                    "orderId" to taskQueueDelegate.currentOrderKey.value?.orderId,
                    "planId" to taskQueueDelegate.currentOrderKey.value?.planId,
                    "taskCount" to taskQueueDelegate.getTasksSnapshot().size,
                ),
            )
            Result.failure(e)
        }
    }

    private fun extractUploadedKeys(tasks: List<com.ytone.longcare.model.ImageTask>): Map<ImageTaskType, List<String>> {
        return tasks
            .filter { it.status == ImageTaskStatus.SUCCESS && it.isUploaded && it.key != null }
            .groupBy { it.taskType }
            .mapValues { entry -> entry.value.mapNotNull { it.key } }
    }

    private suspend fun getCurrentUser(): CurrentUser? {
        return when (val sessionState = userSessionRepository.sessionState.value) {
            is SessionState.LoggedIn -> sessionState.user
            else -> null
        }
    }

    private companion object {
        const val PHOTO_DIAGNOSTIC_CATEGORY = "photo_upload"
    }
}
