package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.image.UnifiedImagePipeline
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 图片处理ViewModel
 * 负责管理图片处理队列、状态更新和与UI的交互
 */
@HiltViewModel
class PhotoProcessingViewModel @Inject constructor(
    private val photoCloudUploader: PhotoCloudUploader,
    private val imagePipeline: UnifiedImagePipeline,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
) : ViewModel() {

    private val taskQueueDelegate = PhotoTaskQueueDelegate(
        scope = viewModelScope,
        imageRepository = imageRepository,
        imagePipeline = imagePipeline,
    )

    private val uploadDelegate = PhotoUploadDelegate(
        photoCloudUploader = photoCloudUploader,
        userSessionRepository = userSessionRepository,
        orderDetailRepository = unifiedOrderRepository,
        taskQueueDelegate = taskQueueDelegate,
    )

    val currentOrderKey: StateFlow<OrderKey?> = taskQueueDelegate.currentOrderKey.asStateFlow()
    val imageTasks: StateFlow<List<ImageTask>> = taskQueueDelegate.imageTasks.asStateFlow()
    val isProcessing: StateFlow<Boolean> = taskQueueDelegate.isProcessing.asStateFlow()
    val isUploading: StateFlow<Boolean> = uploadDelegate.isUploading.asStateFlow()
    val currentTaskType: StateFlow<ImageTaskType?> = taskQueueDelegate.currentTaskType.asStateFlow()

    fun setCurrentTaskType(taskType: ImageTaskType) {
        taskQueueDelegate.setCurrentTaskType(taskType)
    }

    fun setOrderKey(orderKey: OrderKey) {
        taskQueueDelegate.setOrderKey(orderKey)
    }

    fun addImageToProcess(uri: Uri, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        taskQueueDelegate.addImageToProcess(uri, taskType, address, orderKey)
    }

    fun addImagesToProcess(uris: List<Uri>, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        taskQueueDelegate.addImagesToProcess(uris, taskType, address, orderKey)
    }

    suspend fun generateWatermarkData(taskType: ImageTaskType, address: String, orderId: Long? = null): WatermarkData {
        return uploadDelegate.generateWatermarkData(taskType, address, orderId)
    }

    fun retryTask(taskId: String) {
        taskQueueDelegate.retryTask(taskId)
    }

    fun removeTask(taskId: String) {
        taskQueueDelegate.removeTask(taskId)
    }

    fun clearAllTasks() {
        taskQueueDelegate.clearAllTasks()
    }

    fun getSuccessfulImageUris(): Map<ImageTaskType, List<String>> {
        return taskQueueDelegate.getSuccessfulImageUris()
    }

    suspend fun uploadSuccessfulImagesToCloud(): Result<Map<ImageTaskType, List<String>>> {
        return uploadDelegate.uploadSuccessfulImagesToCloud()
    }

    fun getTasksByStatus(status: ImageTaskStatus): List<ImageTask> {
        return taskQueueDelegate.getTasksByStatus(status)
    }

    fun getTasksByType(taskType: ImageTaskType): List<ImageTask> {
        return taskQueueDelegate.getTasksByType(taskType)
    }

    fun getBeforeCareTasks(): List<ImageTask> {
        return taskQueueDelegate.getBeforeCareTasks()
    }

    fun getAfterCareTasks(): List<ImageTask> {
        return taskQueueDelegate.getAfterCareTasks()
    }

    fun hasProcessingTasks(): Boolean {
        return taskQueueDelegate.hasProcessingTasks()
    }

    fun hasFailedTasks(): Boolean {
        return taskQueueDelegate.hasFailedTasks()
    }

    fun loadExistingImageTasks(existingImageTasks: Map<ImageTaskType, List<ImageTask>>) {
        taskQueueDelegate.loadExistingImageTasks(existingImageTasks)
    }

    fun getTaskStats(): TaskStats {
        return taskQueueDelegate.getTaskStats()
    }

}
