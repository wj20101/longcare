package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.order.CheckoutPhotos
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.ImageUploadStatus
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.common.utils.logI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class ServiceCountdownImageDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val imageRepository: OrderImageRepository,
    private val viewModelScope: CoroutineScope,
) {
    private var loadVersion = 0L
    private var currentOrder: OrderKey? = null

    suspend fun validatePhotosUploaded(orderKey: OrderKey): Boolean {
        val images = loadImages(orderKey) ?: return false
        return CheckoutPhotos.from(orderKey, images) != null
    }

    private suspend fun loadImages(orderKey: OrderKey): List<OrderImageEntity>? {
        val version = ++loadVersion
        if (currentOrder != orderKey) stateHolder.uploadedImages.value = emptyMap()
        currentOrder = orderKey
        val images = imageRepository.getImagesByOrderId(orderKey)
        currentCoroutineContext().ensureActive()
        // A later read may belong to another order or observe removed photos. Its result
        // supersedes both this display update and this request's permission to continue.
        if (version != loadVersion) return null
        val groupedImages = images
            .filter { it.orderId == orderKey.orderId && it.getUploadStatusEnum() == ImageUploadStatus.SUCCESS && !it.cloudKey.isNullOrBlank() }
            .mapNotNull { entity ->
                entity.toTaskType()?.let { taskType -> taskType to entity.toImageTask(taskType) }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second }
            )

        stateHolder.uploadedImages.value = groupedImages

        logI(
            "loadImages: Loaded ${images.size} images from DB for order $orderKey. Grouped: ${groupedImages.mapValues { it.value.size }}"
        )
        return images
    }

    fun loadUploadedImagesFromRepository(orderKey: OrderKey) {
        viewModelScope.launch {
            loadImages(orderKey)
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
