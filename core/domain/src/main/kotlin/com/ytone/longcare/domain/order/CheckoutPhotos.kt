package com.ytone.longcare.domain.order

import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.ImageUploadStatus
import com.ytone.longcare.model.OrderImageEntity
import com.ytone.longcare.model.OrderKey

/** One validated Room snapshot supplies both the checkout gate and its request arrays. */
data class CheckoutPhotos(
    val before: List<String>,
    val center: List<String>,
    val after: List<String>,
) {
    companion object {
        fun from(orderKey: OrderKey, images: List<OrderImageEntity>): CheckoutPhotos? {
            val serviceImages = images.filter { it.getImageTypeEnum() in serviceTypes }
            if (serviceImages.any { it.orderId != orderKey.orderId ||
                    it.getUploadStatusEnum() != ImageUploadStatus.SUCCESS || it.cloudKey.isNullOrBlank() }) return null
            fun keys(type: ImageType) = serviceImages.filter { it.getImageTypeEnum() == type }
                .map { requireNotNull(it.cloudKey) }
            val before = keys(ImageType.BEFORE_CARE)
            val after = keys(ImageType.AFTER_CARE)
            if (before.isEmpty() || after.isEmpty()) return null
            return CheckoutPhotos(before, keys(ImageType.CENTER_CARE), after)
        }

        private val serviceTypes = setOf(ImageType.BEFORE_CARE, ImageType.CENTER_CARE, ImageType.AFTER_CARE)
    }
}
