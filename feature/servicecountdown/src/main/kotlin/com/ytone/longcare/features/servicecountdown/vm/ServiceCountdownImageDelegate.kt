package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.logI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class ServiceCountdownImageDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val imageRepository: OrderImageRepository,
    private val viewModelScope: CoroutineScope,
) {
    fun handlePhotoUploadResult(uploadResult: Map<ImageTaskType, List<ImageTask>>) {
        stateHolder.uploadedImages.value = uploadResult
    }

    fun getCurrentUploadedImages(): Map<ImageTaskType, List<ImageTask>> {
        return stateHolder.uploadedImages.value
    }

    fun validatePhotosUploaded(): Boolean {
        val images = stateHolder.uploadedImages.value
        val beforeCareTasks = images[ImageTaskType.BEFORE_CARE] ?: emptyList()
        val afterCareTasks = images[ImageTaskType.AFTER_CARE] ?: emptyList()
        return beforeCareTasks.isNotEmpty() && afterCareTasks.isNotEmpty()
    }

    suspend fun getUploadedImagesSuspend(orderKey: OrderKey): Map<ImageTaskType, List<ImageTask>> {
        val images = imageRepository.getImagesByOrderId(orderKey)
        val groupedImages = images
            .mapNotNull { entity ->
                entity.toTaskType()?.let { taskType -> taskType to entity.toImageTask(taskType) }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )

        if (groupedImages.isNotEmpty()) {
            stateHolder.uploadedImages.value = groupedImages
        }

        logI(
            "getUploadedImagesSuspend: Loaded ${images.size} images from DB for order $orderKey. Grouped: ${groupedImages.mapValues { it.value.size }}"
        )
        return groupedImages
    }

    fun loadUploadedImagesFromRepository(orderKey: OrderKey) {
        viewModelScope.launch {
            getUploadedImagesSuspend(orderKey)
        }
    }

    suspend fun hasLocalUploadedImages(orderKey: OrderKey): Boolean {
        return imageRepository.getImagesByOrderId(orderKey).isNotEmpty()
    }

    fun clearUploadedImagesFromLocal(orderKey: OrderKey) {
        viewModelScope.launch {
            imageRepository.deleteImagesByOrderId(orderKey)
            stateHolder.uploadedImages.value = emptyMap()
        }
    }

    private fun OrderImageEntity.toTaskType(): ImageTaskType? {
        return when (getImageTypeEnum()) {
            ImageType.BEFORE_CARE -> ImageTaskType.BEFORE_CARE
            ImageType.CENTER_CARE -> ImageTaskType.CENTER_CARE
            ImageType.AFTER_CARE -> ImageTaskType.AFTER_CARE
            else -> null
        }
    }

    private fun OrderImageEntity.toImageTask(taskType: ImageTaskType): ImageTask {
        return ImageTask(
            id = id.toString(),
            originalUri = localUri,
            taskType = taskType,
            key = cloudKey,
            cloudUrl = cloudUrl
        )
    }
}
