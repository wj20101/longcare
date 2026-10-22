package com.ytone.longcare.features.photoupload.viewmodel

import android.net.Uri
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.ImageUploadStatus
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class PhotoTaskQueueDelegate(
    private val scope: CoroutineScope,
    private val imageRepository: OrderImageRepository,
    private val imagePipeline: UnifiedImagePipeline,
    private val userMessages: PhotoUploadMessages,
) {
    val currentOrderKey = MutableStateFlow<OrderKey?>(null)
    val imageTasks = MutableStateFlow<List<ImageTask>>(emptyList())
    val isProcessing = MutableStateFlow(false)
    val currentTaskType = MutableStateFlow<ImageTaskType?>(null)
    private val addImagesMutex = Mutex()
    // Keep the managed image visible/retryable if its first Room insert fails.
    private val pendingPersistence = mutableMapOf<String, OrderKey>()
    private val localTaskOrders = mutableMapOf<String, OrderKey>()
    private val removedLocalUris = mutableSetOf<Pair<OrderKey, String>>()

    fun setCurrentTaskType(taskType: ImageTaskType) {
        currentTaskType.value = taskType
    }

    fun setOrderKey(orderKey: OrderKey) {
        logD("setOrderKey: $orderKey (current: ${currentOrderKey.value})", tag = "PhotoVM")
        if (currentOrderKey.value == orderKey) return
        currentOrderKey.value = orderKey
        loadImagesFromRoom(orderKey)
    }

    suspend fun addImagesToProcess(
        uris: List<Uri>,
        taskType: ImageTaskType,
        address: String,
        orderKey: OrderKey? = null,
        maxPhotosPerCategory: Int? = null,
    ): AddImagesResult = addImagesMutex.withLock {
        val currentCount = imageTasks.value.count { it.taskType == taskType }
        val acceptedCount = ServicePhotoLimitPolicy.allowedIncomingCount(
            currentCount = currentCount,
            requestedCount = uris.size,
            maxCount = maxPhotosPerCategory,
        )
        val acceptedUris = uris.take(acceptedCount)
        val rejectedUris = uris.drop(acceptedCount)
        val effectiveOrderKey = orderKey ?: currentOrderKey.value
        logD(
            "addImagesToProcess: requested=${uris.size}, accepted=${acceptedUris.size}, key=$effectiveOrderKey",
            tag = "PhotoVM",
        )
        val newTasks = mutableListOf<ImageTask>()

        for (uri in acceptedUris) {
            val taskId = UUID.randomUUID().toString()
            if (effectiveOrderKey != null) {
                pendingPersistence[taskId] = effectiveOrderKey
                localTaskOrders[taskId] = effectiveOrderKey
            } else {
                logW("No effectiveOrderKey, using UUID", tag = "PhotoVM")
            }

            newTasks += ImageTask(
                id = taskId,
                originalUri = uri.toString(),
                taskType = taskType,
                status = ImageTaskStatus.PROCESSING
            )
        }

        imageTasks.update { it + newTasks }
        newTasks.forEach { processImageTask(it) }
        if (rejectedUris.isNotEmpty()) imagePipeline.deleteManagedImages(rejectedUris)

        AddImagesResult(
            rejectedCount = rejectedUris.size,
        )
    }

    fun retryTask(taskId: String) {
        val task = imageTasks.value.find { it.id == taskId } ?: return
        if (task.status == ImageTaskStatus.FAILED) {
            updateTaskStatus(taskId, ImageTaskStatus.PROCESSING)
            processImageTask(task.copy(status = ImageTaskStatus.PROCESSING))
        }
    }

    fun removeTask(taskId: String) {
        val removedTask = imageTasks.value.find { it.id == taskId }
        val orderKey = localTaskOrders[taskId] ?: currentOrderKey.value
        if (removedTask != null && orderKey != null) {
            removedLocalUris += orderKey to removedTask.originalUri
        }
        pendingPersistence.remove(taskId)
        localTaskOrders.remove(taskId)
        imageTasks.value = imageTasks.value.filter { it.id != taskId }
        scope.launch {
            taskId.toLongOrNull()?.let { imageRepository.deleteImage(it) }
            removedTask
                ?.localUris()
                ?.filterNot { uri -> imageTasks.value.any { remaining -> uri in remaining.localUris() } }
                ?.forEach { imagePipeline.deleteManagedImage(Uri.parse(it)) }
        }
    }

    fun clearAllTasks() {
        val removedTasks = imageTasks.value
        val orderKey = currentOrderKey.value
        removedTasks.forEach { task ->
            (localTaskOrders[task.id] ?: orderKey)?.let { removedLocalUris += it to task.originalUri }
        }
        pendingPersistence.clear()
        localTaskOrders.clear()
        imageTasks.value = emptyList()
        scope.launch {
            orderKey?.let { imageRepository.deleteImagesByOrderId(it) }
            imagePipeline.deleteManagedImages(
                removedTasks
                    .flatMap { task -> task.localUris() }
                    .distinct()
                    .map(Uri::parse)
            )
        }
    }

    fun getSuccessfulImageUris(): Map<ImageTaskType, List<String>> {
        return imageTasks.value
            .filter { it.status == ImageTaskStatus.SUCCESS && it.resultUri != null }
            .groupBy { it.taskType }
            .mapValues { it.value.mapNotNull(ImageTask::resultUri) }
    }

    fun getTasksByStatus(status: ImageTaskStatus): List<ImageTask> = imageTasks.value.filter { it.status == status }
    fun getTasksByType(taskType: ImageTaskType): List<ImageTask> = imageTasks.value.filter { it.taskType == taskType }
    fun getBeforeCareTasks(): List<ImageTask> = getTasksByType(ImageTaskType.BEFORE_CARE)
    fun getAfterCareTasks(): List<ImageTask> = getTasksByType(ImageTaskType.AFTER_CARE)
    fun hasProcessingTasks(): Boolean = imageTasks.value.any { it.status == ImageTaskStatus.PROCESSING }
    fun hasFailedTasks(): Boolean = imageTasks.value.any { it.status == ImageTaskStatus.FAILED }

    fun loadExistingImageTasks(existingImageTasks: Map<ImageTaskType, List<ImageTask>>) {
        imageTasks.value = imageTasks.value + existingImageTasks.values.flatten()
    }

    fun getTaskStats(): TaskStats {
        val tasks = imageTasks.value
        return TaskStats(
            total = tasks.size,
            processing = tasks.count { it.status == ImageTaskStatus.PROCESSING },
            success = tasks.count { it.status == ImageTaskStatus.SUCCESS },
            failed = tasks.count { it.status == ImageTaskStatus.FAILED }
        )
    }

    fun getTasksSnapshot(): List<ImageTask> = imageTasks.value

    suspend fun updateTaskUploadStatus(taskId: String, key: String) {
        check(imageTasks.value.any { it.id == taskId }) { "Order photo task no longer exists" }
        val databaseId = taskId.toLongOrNull()?.takeIf { it > 0 }
        check(databaseId != null || (currentOrderKey.value == null && taskId !in pendingPersistence)) {
            "Order photo has no durable local record"
        }
        databaseId?.let { imageRepository.markAsSuccess(it, key) }
        imageTasks.value = imageTasks.value.map { task ->
            if (task.id == taskId) task.copy(isUploaded = true, cloudUrl = null, key = key) else task
        }
    }

    private fun loadImagesFromRoom(orderKey: OrderKey) {
        scope.launch {
            val entities = imageRepository.getImagesByOrderId(orderKey)
            logD("loadImagesFromRoom: orderId=${orderKey.orderId}, found ${entities.size} entities", tag = "PhotoVM")
            if (currentOrderKey.value != orderKey) return@launch
            imageTasks.update { current ->
                // A delayed initial query must not discard photos captured while it was loading.
                val localTasks = current.filter { localTaskOrders[it.id] == orderKey }
                val localIds = localTasks.mapTo(mutableSetOf()) { it.id }
                // Room can expose a committed row before its insert continuation supplies the id.
                val pendingUris = localTasks.filter { it.id in pendingPersistence }
                    .mapTo(mutableSetOf()) { it.originalUri }
                entities.filterNot {
                    it.id.toString() in localIds || it.localUri in pendingUris ||
                        (orderKey to it.localUri) in removedLocalUris
                }
                    .map { it.toImageTask() } + localTasks
            }
        }
    }

    private fun processImageTask(task: ImageTask) {
        val userId = DiagnosticEventTracker.currentUserId()
        scope.launch {
            isProcessing.value = true
            try {
                val orderKey = pendingPersistence[task.id]
                if (orderKey == null) {
                    updateTaskStatus(task.id, ImageTaskStatus.SUCCESS, resultUri = task.originalUri)
                } else {
                    val databaseId = imageRepository.addImage(
                        orderKey = orderKey,
                        imageType = task.taskType.toImageType(),
                        localUri = task.originalUri,
                        localPath = Uri.parse(task.originalUri).path,
                    )
                    check(databaseId > 0) { "Order photo insert did not create a local record" }
                    if (pendingPersistence[task.id] != orderKey || imageTasks.value.none { it.id == task.id }) {
                        // The user removed the task while Room was writing it.
                        imageRepository.deleteImage(databaseId)
                        return@launch
                    }
                    pendingPersistence.remove(task.id)
                    localTaskOrders.remove(task.id)
                    localTaskOrders[databaseId.toString()] = orderKey
                    imageTasks.update { tasks ->
                        tasks.map { current ->
                            if (current.id == task.id) current.copy(
                                id = databaseId.toString(),
                                status = ImageTaskStatus.SUCCESS,
                                resultUri = task.originalUri,
                                errorMessage = null,
                            ) else current
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DiagnosticEventTracker.trackError(
                    userId = userId,
                    category = PHOTO_DIAGNOSTIC_CATEGORY,
                    event = "image_task_process_exception",
                    description = "服务照片任务处理异常",
                    throwable = e,
                    extras = mapOf(
                        "orderId" to currentOrderKey.value?.orderId,
                        "planId" to currentOrderKey.value?.planId,
                        "taskType" to task.taskType.name,
                        "taskIdLength" to task.id.length,
                    ),
                )
                updateTaskStatus(
                    task.id,
                    ImageTaskStatus.FAILED,
                    errorMessage = userMessages.imageProcessingFailed,
                )
            } finally {
                isProcessing.value = imageTasks.value.any { it.status == ImageTaskStatus.PROCESSING }
            }
        }
    }

    private fun updateTaskStatus(
        taskId: String,
        status: ImageTaskStatus,
        resultUri: String? = null,
        errorMessage: String? = null
    ) {
        imageTasks.value = imageTasks.value.map { task ->
            if (task.id == taskId) task.copy(status = status, resultUri = resultUri, errorMessage = errorMessage) else task
        }
    }

    private fun OrderImageEntity.toImageTask(): ImageTask {
        return ImageTask(
            id = id.toString(),
            originalUri = localUri,
            taskType = getImageTypeEnum().toImageTaskType(),
            resultUri = localUri,
            status = getUploadStatusEnum().toImageTaskStatus(),
            errorMessage = errorMessage,
            isUploaded = uploadStatus == ImageUploadStatus.SUCCESS.value,
            key = cloudKey,
            cloudUrl = cloudUrl
        )
    }

    private fun ImageType.toImageTaskType(): ImageTaskType = when (this) {
        ImageType.CUSTOMER -> ImageTaskType.BEFORE_CARE
        ImageType.BEFORE_CARE -> ImageTaskType.BEFORE_CARE
        ImageType.CENTER_CARE -> ImageTaskType.CENTER_CARE
        ImageType.AFTER_CARE -> ImageTaskType.AFTER_CARE
    }

    private fun ImageTaskType.toImageType(): ImageType = when (this) {
        ImageTaskType.BEFORE_CARE -> ImageType.BEFORE_CARE
        ImageTaskType.CENTER_CARE -> ImageType.CENTER_CARE
        ImageTaskType.AFTER_CARE -> ImageType.AFTER_CARE
    }

    private fun ImageUploadStatus.toImageTaskStatus(): ImageTaskStatus = when (this) {
        ImageUploadStatus.PENDING, ImageUploadStatus.UPLOADING, ImageUploadStatus.SUCCESS -> ImageTaskStatus.SUCCESS
        ImageUploadStatus.FAILED, ImageUploadStatus.CANCELLED -> ImageTaskStatus.FAILED
    }

    private fun ImageTask.localUris(): Set<String> =
        setOfNotNull(originalUri, resultUri)

    private companion object {
        val PHOTO_DIAGNOSTIC_CATEGORY = DiagnosticCategory.PHOTO_UPLOAD
    }
}

data class TaskStats(
    val total: Int,
    val processing: Int,
    val success: Int,
    val failed: Int
)
