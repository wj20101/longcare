package com.ytone.longcare.domain.repository

import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ImageType
import com.ytone.longcare.model.OrderImageEntity

interface OrderImageRepository {
    suspend fun getImagesByOrderId(orderKey: OrderKey): List<OrderImageEntity>
    suspend fun addImage(
        orderKey: OrderKey,
        imageType: ImageType,
        localUri: String,
        localPath: String? = null
    ): Long
    suspend fun markAsSuccess(imageId: Long, cloudKey: String)
    suspend fun deleteImage(imageId: Long)
    suspend fun deleteImagesByOrderId(orderKey: OrderKey)
}
