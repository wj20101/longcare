package com.ytone.longcare.features.photoupload.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * 图片处理ViewModel
 * 负责管理图片处理队列、状态更新和与UI的交互
 */
@HiltViewModel
class PhotoProcessingViewModel @Inject constructor(
    @param:ApplicationContext private val applicationContext: Context,
    private val toastHelper: ToastHelper,
    private val cosRepository: CosRepository,
    private val userSessionRepository: UserSessionRepository,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val runtimeConfigProvider: RuntimeConfigProvider,
) : ViewModel() {

    private val taskQueueDelegate = PhotoTaskQueueDelegate(
        scope = viewModelScope,
        imageRepository = imageRepository,
    )

    private val uploadDelegate = PhotoUploadDelegate(
        applicationContext = applicationContext,
        cosRepository = cosRepository,
        userSessionRepository = userSessionRepository,
        orderDetailRepository = unifiedOrderRepository,
        taskQueueDelegate = taskQueueDelegate,
    )

    val isMockDataEnabled: Boolean
        get() = runtimeConfigProvider.useMockData

    val currentOrderKey: StateFlow<OrderKey?> = taskQueueDelegate.currentOrderKey.asStateFlow()
    val imageTasks: StateFlow<List<ImageTask>> = taskQueueDelegate.imageTasks.asStateFlow()
    val isProcessing: StateFlow<Boolean> = taskQueueDelegate.isProcessing.asStateFlow()
    val isUploading: StateFlow<Boolean> = uploadDelegate.isUploading.asStateFlow()
    val currentTaskType: StateFlow<ImageTaskType?> = taskQueueDelegate.currentTaskType.asStateFlow()

    fun showToast(string: String) {
        toastHelper.showShort(string)
    }

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

    fun mockAddUploadedPhoto(taskType: ImageTaskType) {
        taskQueueDelegate.mockAddUploadedPhoto(taskType)
    }

    fun mockAddBeforeCarePhoto() {
        taskQueueDelegate.mockAddBeforeCarePhoto()
    }

    fun mockAddCenterCarePhoto() {
        taskQueueDelegate.mockAddCenterCarePhoto()
    }

    fun mockAddAfterCarePhoto() {
        taskQueueDelegate.mockAddAfterCarePhoto()
    }

    fun mockAddAllPhotos() {
        taskQueueDelegate.mockAddAllPhotos()
    }
}
