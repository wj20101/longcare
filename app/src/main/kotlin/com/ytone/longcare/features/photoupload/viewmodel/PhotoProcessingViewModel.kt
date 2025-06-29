package com.ytone.longcare.features.photoupload.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.getFileName
import com.ytone.longcare.common.utils.getFileExtension
import com.ytone.longcare.data.cos.model.UploadParams
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskStatus
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.utils.ImageProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
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
    private val cosRepository: CosRepository
) : ViewModel() {

    companion object {
        private const val DEFAULT_FOLDER_TYPE = 13 // 默认文件夹类型
    }

    private val imageProcessor = ImageProcessor(applicationContext)

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

    /**
     * 添加单张图片到处理队列
     */
    fun addImageToProcess(uri: Uri, taskType: ImageTaskType, watermarkContent: String) {
        addImagesToProcess(listOf(uri), taskType, watermarkContent)
    }

    /**
     * 添加多张图片到处理队列
     */
    fun addImagesToProcess(uris: List<Uri>, taskType: ImageTaskType, watermarkContent: String) {
        val newTasks = uris.map { uri ->
            ImageTask(
                id = UUID.randomUUID().toString(),
                originalUri = uri,
                taskType = taskType,
                watermarkContent = watermarkContent,
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

    /**
     * 处理单个图片任务
     */
    private fun processImageTask(task: ImageTask) {
        viewModelScope.launch {
            _isProcessing.value = true

            try {
                val result = imageProcessor.processImage(task.originalUri, task.watermarkContent)

                if (result.isSuccess) {
                    // 处理成功
                    updateTaskStatus(
                        task.id, ImageTaskStatus.SUCCESS, resultUri = result.getOrNull()
                    )
                } else {
                    // 处理失败
                    updateTaskStatus(
                        task.id,
                        ImageTaskStatus.FAILED,
                        errorMessage = result.exceptionOrNull()?.message ?: "处理失败"
                    )
                }
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
    fun getSuccessfulImageUris(): Map<String, List<String>> {
        return _imageTasks.value
            .filter { it.status == ImageTaskStatus.SUCCESS && it.resultUri != null }
            .groupBy { it.taskType.name }
            .mapValues { entry -> entry.value.mapNotNull { it.resultUri?.toString() } }
    }

    /**
     * 上传成功的图片到云端
     * @return 上传成功的云端URL列表，按任务类型分组
     */
    suspend fun uploadSuccessfulImagesToCloud(): Result<Map<ImageTaskType, List<String>>> {
        return try {
            _isUploading.value = true
            
            val successfulTasks = _imageTasks.value.filter { 
                it.status == ImageTaskStatus.SUCCESS && it.resultUri != null 
            }
            
            if (successfulTasks.isEmpty()) {
                _isUploading.value = false
                return Result.success(emptyMap())
            }
            
            val uploadResults = mutableMapOf<ImageTaskType, MutableList<String>>()
            
            for (task in successfulTasks) {
                val uri = task.resultUri ?: continue
                
                val uploadParams = CosUtils.createUploadParams(
                    context = applicationContext,
                    fileUri = uri,
                    folderType = DEFAULT_FOLDER_TYPE
                )
                
                val uploadResult = cosRepository.uploadFile(uploadParams)
                
                if (uploadResult.success && uploadResult.url != null) {
                    uploadResults.getOrPut(task.taskType) { mutableListOf() }.add(uploadResult.url)
                } else {
                    _isUploading.value = false
                    return Result.failure(Exception("上传失败: ${uploadResult.errorMessage}"))
                }
            }
            
            _isUploading.value = false
            Result.success(uploadResults.toMap())
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