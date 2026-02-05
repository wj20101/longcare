package com.ytone.longcare.features.photoupload.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.database.entity.ImageType
import com.ytone.longcare.data.database.entity.ImageUploadStatus
import com.ytone.longcare.data.database.entity.OrderImageEntity
import com.ytone.longcare.data.repository.ImageRepository
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.features.photoupload.ui.PhotoCategory
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject
import androidx.core.net.toUri

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
    private val unifiedOrderRepository: UnifiedOrderRepository,
    private val imageRepository: ImageRepository,
) : ViewModel() {

    // 当前订单Key，用于Room持久化
    private val _currentOrderKey = MutableStateFlow<OrderKey?>(null)
    val currentOrderKey: StateFlow<OrderKey?> = _currentOrderKey.asStateFlow()

    // 图片任务列表的私有状态
    private val _imageTasks = MutableStateFlow<List<ImageTask>>(emptyList())

    // 对外暴露的只读状态
    val imageTasks: StateFlow<List<ImageTask>> = _imageTasks.asStateFlow()

    // 是否正在处理图片的状态
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // 是否正在上传到云端的状态
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    fun showToast(string: String) {
        toastHelper.showShort(string)
    }

    private val _currentCategory = MutableStateFlow<PhotoCategory?>(null)
    val currentCategory: StateFlow<PhotoCategory?> = _currentCategory.asStateFlow()

    fun setCurrentCategory(category: PhotoCategory) {
        _currentCategory.value = category
    }

    /**
     * 设置当前订单Key并从Room加载图片
     * @param orderKey 订单标识符
     */
    fun setOrderKey(orderKey: OrderKey) {
        android.util.Log.d("PhotoVM", "setOrderKey: $orderKey (current: ${_currentOrderKey.value})")
        if (_currentOrderKey.value == orderKey) return
        _currentOrderKey.value = orderKey
        loadImagesFromRoom(orderKey)
    }

    /**
     * 从Room加载订单图片
     */
    private fun loadImagesFromRoom(orderKey: OrderKey) {
        viewModelScope.launch {
            val entities = imageRepository.getImagesByOrderId(orderKey)
            android.util.Log.d("PhotoVM", "loadImagesFromRoom: orderId=${orderKey.orderId}, found ${entities.size} entities")
            entities.forEach { 
                android.util.Log.d("PhotoVM", "  - Image: id=${it.id}, uri=${it.localUri}, status=${it.uploadStatus}")
            }
            val tasks = entities.map { it.toImageTask() }
            _imageTasks.value = tasks
        }
    }

    /**
     * OrderImageEntity转换为ImageTask
     */
    private fun OrderImageEntity.toImageTask(): ImageTask {
        return ImageTask(
            id = id.toString(),
            originalUri = localUri.toUri(),
            taskType = getImageTypeEnum().toImageTaskType(),
            resultUri = localUri.toUri(),
            status = getUploadStatusEnum().toImageTaskStatus(),
            errorMessage = errorMessage,
            isUploaded = uploadStatus == ImageUploadStatus.SUCCESS.value,
            key = cloudKey,
            cloudUrl = cloudUrl
        )
    }

    /**
     * ImageType转换为ImageTaskType
     */
    private fun ImageType.toImageTaskType(): ImageTaskType {
        return when (this) {
            ImageType.CUSTOMER -> ImageTaskType.BEFORE_CARE
            ImageType.BEFORE_CARE -> ImageTaskType.BEFORE_CARE
            ImageType.CENTER_CARE -> ImageTaskType.CENTER_CARE
            ImageType.AFTER_CARE -> ImageTaskType.AFTER_CARE
        }
    }

    /**
     * ImageTaskType转换为ImageType
     */
    private fun ImageTaskType.toImageType(): ImageType {
        return when (this) {
            ImageTaskType.BEFORE_CARE -> ImageType.BEFORE_CARE
            ImageTaskType.CENTER_CARE -> ImageType.CENTER_CARE
            ImageTaskType.AFTER_CARE -> ImageType.AFTER_CARE
        }
    }

    /**
     * ImageUploadStatus转换为ImageTaskStatus
     */
    private fun ImageUploadStatus.toImageTaskStatus(): ImageTaskStatus {
        return when (this) {
            // PENDING/UPLOADING 在重新加载时应视为本地已就绪 (SUCCESS)，
            // 因为没有后台进程在跑，且文件存在。这允许用户再次点击上传按钮。
            ImageUploadStatus.PENDING, ImageUploadStatus.UPLOADING -> ImageTaskStatus.SUCCESS 
            ImageUploadStatus.SUCCESS -> ImageTaskStatus.SUCCESS
            ImageUploadStatus.FAILED, ImageUploadStatus.CANCELLED -> ImageTaskStatus.FAILED
        }
    }

    /**
     * 添加单张图片到处理队列
     */
    fun addImageToProcess(uri: Uri, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        addImagesToProcess(listOf(uri), taskType, address, orderKey)
    }

    /**
     * 添加多张图片到处理队列
     */
    fun addImagesToProcess(uris: List<Uri>, taskType: ImageTaskType, address: String, orderKey: OrderKey? = null) {
        viewModelScope.launch {
            // 使用传入的orderKey或当前订单Key
            val effectiveOrderKey = orderKey ?: _currentOrderKey.value
            android.util.Log.d("PhotoVM", "addImagesToProcess: count=${uris.size}, key=$effectiveOrderKey")
            
            val newTasks = mutableListOf<ImageTask>()
            
            for (uri in uris) {
                // 如果有订单Key，先保存到Room，获取数据库ID
                val dbId = if (effectiveOrderKey != null) {
                    try {
                        val id = imageRepository.addImage(
                            orderKey = effectiveOrderKey,
                            imageType = taskType.toImageType(),
                            localUri = uri.toString(),
                            localPath = uri.path
                        )
                        android.util.Log.d("PhotoVM", "Saved to DB: id=$id, type=$taskType")
                        id
                    } catch (e: Exception) {
                        android.util.Log.e("PhotoVM", "Failed to save image to DB", e)
                        null
                    }
                } else {
                    // 没有订单Key时使用UUID作为临时ID
                    android.util.Log.w("PhotoVM", "No effectiveOrderKey, using UUID")
                    null
                }
                
                val task = ImageTask(
                    id = dbId?.toString() ?: UUID.randomUUID().toString(),
                    originalUri = uri,
                    taskType = taskType,
                    status = ImageTaskStatus.PROCESSING
                )
                newTasks.add(task)
            }

            // 更新任务列表
            _imageTasks.update { it + newTasks }

            // 开始处理每个任务
            newTasks.forEach { task ->
                processImageTask(task)
            }
        }
    }

    /**
     * 生成水印数据对象
     */
    suspend fun generateWatermarkData(taskType: ImageTaskType, address: String, orderId: Long? = null): WatermarkData {
        val watermarkTitle = when (taskType) {
            ImageTaskType.BEFORE_CARE -> "服务前"
            ImageTaskType.CENTER_CARE -> "服务中"
            ImageTaskType.AFTER_CARE -> "服务后"
        }

        // 获取当前登录用户（护工）信息
        val currentUser = getCurrentUser()
        val caregiverName = currentUser?.userName ?: "未知护工"

        // 获取老人信息
        val elderName = if (orderId != null) {
            val orderInfo = unifiedOrderRepository.getCachedOrderInfo(OrderKey(orderId))
            orderInfo?.userInfo?.name ?: "未知老人"
        } else {
            "未知老人"
        }

        return WatermarkData(
            title = watermarkTitle,
            insuredPerson = elderName,
            caregiver = caregiverName,
            address = address
        )
    }

    /**
     * 获取当前登录用户
     */
    private suspend fun getCurrentUser(): User? {
        return when (val sessionState = userSessionRepository.sessionState.value) {
            is SessionState.LoggedIn -> sessionState.user
            else -> null
        }
    }

    /**
     * 处理单个图片任务
     */
    private fun processImageTask(task: ImageTask) {
        viewModelScope.launch {
            _isProcessing.value = true
            try {
                // 处理成功
                updateTaskStatus(
                    task.id, ImageTaskStatus.SUCCESS, resultUri = task.originalUri
                )
            } catch (e: Exception) {
                // 异常处理
                updateTaskStatus(
                    task.id, ImageTaskStatus.FAILED, errorMessage = e.message ?: "未知错误"
                )
            } finally {
                // 检查是否还有正在处理的任务
                val hasProcessingTasks =
                    _imageTasks.value.any { it.status == ImageTaskStatus.PROCESSING }
                _isProcessing.value = hasProcessingTasks
            }
        }
    }

    /**
     * 更新任务状态
     */
    private fun updateTaskStatus(
        taskId: String,
        status: ImageTaskStatus,
        resultUri: Uri? = null,
        errorMessage: String? = null
    ) {
        _imageTasks.value = _imageTasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = status, resultUri = resultUri, errorMessage = errorMessage
                )
            } else {
                task
            }
        }
    }

    /**
     * 更新任务上传状态
     */
    private fun updateTaskUploadStatus(taskId: String, cloudUrl: String, key: String) {
        _imageTasks.value = _imageTasks.value.map { task ->
            if (task.id == taskId) {
                task.copy(isUploaded = true, cloudUrl = cloudUrl, key = key)
            } else {
                task
            }
        }
        
        // 同步到Room
        viewModelScope.launch {
            val imageId = taskId.toLongOrNull()
            if (imageId != null) {
                imageRepository.markAsSuccess(imageId, key, cloudUrl)
            }
        }
    }

    /**
     * 重试失败的任务
     */
    fun retryTask(taskId: String) {
        val task = _imageTasks.value.find { it.id == taskId }
        if (task != null && task.status == ImageTaskStatus.FAILED) {
            // 重置任务状态为处理中
            updateTaskStatus(taskId, ImageTaskStatus.PROCESSING)
            // 重新处理
            processImageTask(task.copy(status = ImageTaskStatus.PROCESSING))
        }
    }

    /**
     * 删除任务
     */
    fun removeTask(taskId: String) {
        _imageTasks.value = _imageTasks.value.filter { it.id != taskId }
        
        // 同步到Room
        viewModelScope.launch {
            val imageId = taskId.toLongOrNull()
            if (imageId != null) {
                imageRepository.deleteImage(imageId)
            }
        }
    }

    /**
     * 清空所有任务
     */
    fun clearAllTasks() {
        _imageTasks.value = emptyList()
        
        // 同步到Room
        viewModelScope.launch {
            val orderKey = _currentOrderKey.value
            if (orderKey != null) {
                imageRepository.deleteImagesByOrderId(orderKey)
            }
        }
    }

    /**
     * 获取所有成功处理的图片Uri列表
     */
    fun getSuccessfulImageUris(): Map<ImageTaskType, List<String>> {
        return _imageTasks.value
            .filter { it.status == ImageTaskStatus.SUCCESS && it.resultUri != null }
            .groupBy { it.taskType }
            .mapValues { entry -> entry.value.mapNotNull { it.resultUri?.toString() } }
    }

    /**
     * 上传成功的图片到云端
     * @return 上传成功的云端URL列表，按任务类型分组
     */
    suspend fun uploadSuccessfulImagesToCloud(): Result<Map<ImageTaskType, List<String>>> {
        return try {
            _isUploading.value = true

            // 过滤出成功处理但尚未上传的图片任务
            val successfulTasks = _imageTasks.value.filter {
                it.status == ImageTaskStatus.SUCCESS && it.resultUri != null && !it.isUploaded
            }

            if (successfulTasks.isEmpty()) {
                _isUploading.value = false
                // 返回所有已上传的图片URL（包括之前上传的）
                val allUploadedResults = _imageTasks.value
                    .filter { it.status == ImageTaskStatus.SUCCESS && it.isUploaded && it.key != null }
                    .groupBy { it.taskType }
                    .mapValues { entry -> entry.value.mapNotNull { it.key } }
                return Result.success(allUploadedResults)
            }

            for (task in successfulTasks) {
                val uri = task.resultUri ?: continue

                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = uri,
                    folderType = CosConstants.DEFAULT_FOLDER_TYPE
                )

                val uploadResult = cosRepository.uploadFile(uploadParams)

                if (uploadResult.success && uploadResult.url != null && uploadResult.key != null) {
                    // 更新任务状态，标记为已上传并保存云端URL
                    updateTaskUploadStatus(task.id, uploadResult.url, uploadResult.key)
                } else {
                    _isUploading.value = false
                    return Result.failure(Exception("上传失败: ${uploadResult.errorMessage}"))
                }
            }

            // 合并新上传的和之前已上传的图片URL
            val allUploadedResults = _imageTasks.value
                .filter { it.status == ImageTaskStatus.SUCCESS && it.isUploaded && it.key != null }
                .groupBy { it.taskType }
                .mapValues { entry -> entry.value.mapNotNull { it.key } }

            _isUploading.value = false
            Result.success(allUploadedResults)
        } catch (e: Exception) {
            _isUploading.value = false
            Result.failure(e)
        }
    }


    /**
     * 获取指定状态的任务列表
     */
    fun getTasksByStatus(status: ImageTaskStatus): List<ImageTask> {
        return _imageTasks.value.filter { it.status == status }
    }

    /**
     * 获取指定类型的任务列表
     */
    fun getTasksByType(taskType: ImageTaskType): List<ImageTask> {
        return _imageTasks.value.filter { it.taskType == taskType }
    }

    /**
     * 获取护理前任务列表
     */
    fun getBeforeCareTasks(): List<ImageTask> {
        return getTasksByType(ImageTaskType.BEFORE_CARE)
    }

    /**
     * 获取护理后任务列表
     */
    fun getAfterCareTasks(): List<ImageTask> {
        return getTasksByType(ImageTaskType.AFTER_CARE)
    }

    /**
     * 检查是否有任务正在处理
     */
    fun hasProcessingTasks(): Boolean {
        return _imageTasks.value.any { it.status == ImageTaskStatus.PROCESSING }
    }

    /**
     * 检查是否有失败的任务
     */
    fun hasFailedTasks(): Boolean {
        return _imageTasks.value.any { it.status == ImageTaskStatus.FAILED }
    }

    /**
     * 加载已有的图片任务数据
     * @param existingImageTasks 按ImageTaskType分组的ImageTask列表
     */
    fun loadExistingImageTasks(existingImageTasks: Map<ImageTaskType, List<ImageTask>>) {
        val existingTasks = mutableListOf<ImageTask>()
        
        existingImageTasks.forEach { (_, tasks) ->
            existingTasks.addAll(tasks)
        }
        
        // 将已有任务添加到当前任务列表
        _imageTasks.value = _imageTasks.value + existingTasks
    }

    /**
     * 获取任务统计信息
     */
    fun getTaskStats(): TaskStats {
        val tasks = _imageTasks.value
        return TaskStats(
            total = tasks.size,
            processing = tasks.count { it.status == ImageTaskStatus.PROCESSING },
            success = tasks.count { it.status == ImageTaskStatus.SUCCESS },
            failed = tasks.count { it.status == ImageTaskStatus.FAILED })
    }
    
    // =====================================================================
    // Mock 方法（仅用于开发调试）
    // =====================================================================
    
    /**
     * [Mock] 模拟添加指定类型的已上传成功照片
     * 用于开发调试，跳过拍照和上传流程
     */
    fun mockAddUploadedPhoto(taskType: ImageTaskType) {
        val mockTask = ImageTask(
            id = UUID.randomUUID().toString(),
            originalUri = "content://mock/image_${System.currentTimeMillis()}".toUri(),
            taskType = taskType,
            status = ImageTaskStatus.SUCCESS,
            resultUri = "content://mock/result_${System.currentTimeMillis()}".toUri(),
            isUploaded = true,
            cloudUrl = "https://mock.cos.example.com/mock_image_${System.currentTimeMillis()}.jpg",
            key = "mock_key_${System.currentTimeMillis()}"
        )
        _imageTasks.update { it + mockTask }
    }
    
    /**
     * [Mock] 模拟添加护理前照片
     */
    fun mockAddBeforeCarePhoto() {
        mockAddUploadedPhoto(ImageTaskType.BEFORE_CARE)
    }
    
    /**
     * [Mock] 模拟添加护理中照片
     */
    fun mockAddCenterCarePhoto() {
        mockAddUploadedPhoto(ImageTaskType.CENTER_CARE)
    }
    
    /**
     * [Mock] 模拟添加护理后照片
     */
    fun mockAddAfterCarePhoto() {
        mockAddUploadedPhoto(ImageTaskType.AFTER_CARE)
    }
    
    /**
     * [Mock] 一键模拟添加所有类型的照片（护理前、护理中、护理后各一张）
     */
    fun mockAddAllPhotos() {
        mockAddBeforeCarePhoto()
        mockAddCenterCarePhoto()
        mockAddAfterCarePhoto()
    }
}

/**
 * 任务统计数据类
 */
data class TaskStats(
    val total: Int, val processing: Int, val success: Int, val failed: Int
)
