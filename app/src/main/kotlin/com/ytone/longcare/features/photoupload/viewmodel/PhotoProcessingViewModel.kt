package com.ytone.longcare.features.photoupload.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.order.SharedOrderRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
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
    private val sharedOrderRepository: SharedOrderRepository,
    private val compositeLocationProvider: CompositeLocationProvider
) : ViewModel() {

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
     * 添加单张图片到处理队列
     */
    fun addImageToProcess(uri: Uri, taskType: ImageTaskType, address: String, orderId: Long? = null) {
        addImagesToProcess(listOf(uri), taskType, address, orderId)
    }

    /**
     * 添加多张图片到处理队列
     */
    fun addImagesToProcess(uris: List<Uri>, taskType: ImageTaskType, address: String, orderId: Long? = null) {
        viewModelScope.launch {
            val watermarkLines = generateWatermarkLines(taskType, address, orderId)
            val newTasks = uris.map { uri ->
                ImageTask(
                    id = UUID.randomUUID().toString(),
                    originalUri = uri,
                    taskType = taskType,
                    watermarkLines = watermarkLines,
                    status = ImageTaskStatus.PROCESSING
                )
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
     * 生成水印内容
     */
    private suspend fun generateWatermarkLines(taskType: ImageTaskType, address: String, orderId: Long? = null): List<String> {
        val watermarkData = generateWatermarkData(taskType, address, orderId)
        return listOf(
            watermarkData.title,
            watermarkData.insuredPerson,
            watermarkData.caregiver,
            watermarkData.time,
            watermarkData.location,
            watermarkData.address
        )
    }

    /**
     * 生成水印数据对象
     */
    suspend fun generateWatermarkData(taskType: ImageTaskType, address: String, orderId: Long? = null): WatermarkData {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = dateFormat.format(Date())
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
            val orderInfo = sharedOrderRepository.getCachedOrderInfo(OrderInfoRequestModel(orderId = orderId, planId = 0))
            orderInfo?.userInfo?.name ?: "未知老人"
        } else {
            "未知老人"
        }

        // 获取定位信息
        val locationInfo = getCurrentLocationInfo()

        return WatermarkData(
            title = watermarkTitle,
            insuredPerson = "参保人员:$elderName",
            caregiver = "护理人员:$caregiverName",
            time = "拍摄时间:$currentTime",
            location = locationInfo,
            address = "拍摄地址:$address"
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
     * 获取当前定位信息
     */
    private suspend fun getCurrentLocationInfo(): String {
        return try {
            val locationResult = compositeLocationProvider.getCurrentLocation()
            if (locationResult != null) {
                "卫星定位:${locationResult.longitude},${locationResult.latitude}"
            } else {
                "卫星定位:未获取"
            }
        } catch (_: Exception) {
            "卫星定位:获取失败"
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
    }

    /**
     * 清空所有任务
     */
    fun clearAllTasks() {
        _imageTasks.value = emptyList()
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
}

/**
 * 任务统计数据类
 */
data class TaskStats(
    val total: Int, val processing: Int, val success: Int, val failed: Int
)
