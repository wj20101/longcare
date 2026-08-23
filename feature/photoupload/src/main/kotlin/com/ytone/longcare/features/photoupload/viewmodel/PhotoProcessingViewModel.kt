package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.text.ResourceTextResolver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.ServicePhotoConfigProvider
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.features.photoupload.upload.PhotoCloudUploader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
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
    private val servicePhotoConfigProvider: ServicePhotoConfigProvider,
    textResolver: ResourceTextResolver,
) : ViewModel() {

    private val userMessages = textResolver.photoUploadMessages()

    private val taskQueueDelegate = PhotoTaskQueueDelegate(
        scope = viewModelScope,
        imageRepository = imageRepository,
        imagePipeline = imagePipeline,
        userMessages = userMessages,
    )

    private val uploadDelegate = PhotoUploadDelegate(
        photoCloudUploader = photoCloudUploader,
        userSessionRepository = userSessionRepository,
        orderDetailRepository = unifiedOrderRepository,
        taskQueueDelegate = taskQueueDelegate,
        userMessages = userMessages,
    )

    val currentOrderKey: StateFlow<OrderKey?> = taskQueueDelegate.currentOrderKey.asStateFlow()
    val imageTasks: StateFlow<List<ImageTask>> = taskQueueDelegate.imageTasks.asStateFlow()
    val isProcessing: StateFlow<Boolean> = taskQueueDelegate.isProcessing.asStateFlow()
    val isUploading: StateFlow<Boolean> = uploadDelegate.isUploading.asStateFlow()
    val currentTaskType: StateFlow<ImageTaskType?> = taskQueueDelegate.currentTaskType.asStateFlow()

    private val _photoLimitState = MutableStateFlow(ServicePhotoLimitState())
    val photoLimitState: StateFlow<ServicePhotoLimitState> = _photoLimitState.asStateFlow()

    private val eventChannel = Channel<PhotoProcessingEvent>(capacity = Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val configuredMax = try {
                servicePhotoConfigProvider.getMaxServicePhotoCount()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                0
            }
            _photoLimitState.value = ServicePhotoLimitState(
                isLoaded = true,
                maxPhotosPerCategory = ServicePhotoLimitPolicy.normalize(configuredMax),
            )
        }
    }

    fun setCurrentTaskType(taskType: ImageTaskType) {
        taskQueueDelegate.setCurrentTaskType(taskType)
    }

    fun setOrderKey(orderKey: OrderKey) {
        taskQueueDelegate.setOrderKey(orderKey)
    }

    fun addImageToProcess(uri: Uri, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        addImagesToProcess(listOf(uri), taskType, address, orderKey)
    }

    fun addImagesToProcess(uris: List<Uri>, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        viewModelScope.launch {
            val limitState = photoLimitState.first { it.isLoaded }
            val result = taskQueueDelegate.addImagesToProcess(
                uris = uris,
                taskType = taskType,
                address = address,
                orderKey = orderKey,
                maxPhotosPerCategory = limitState.maxPhotosPerCategory,
            )
            val maxCount = limitState.maxPhotosPerCategory
            if (result.rejectedCount > 0 && maxCount != null) {
                eventChannel.send(PhotoProcessingEvent.PhotoLimitReached(maxCount))
            }
        }
    }

    fun canAddPhoto(taskType: ImageTaskType): Boolean {
        val limitState = photoLimitState.value
        return limitState.isLoaded && ServicePhotoLimitPolicy.canAdd(
            currentCount = taskQueueDelegate.getTasksByType(taskType).size,
            maxCount = limitState.maxPhotosPerCategory,
        )
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
