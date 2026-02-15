package com.ytone.longcare.domain.repository

import com.ytone.longcare.data.database.entity.ImageType
import com.ytone.longcare.data.database.entity.OrderImageEntity
import com.ytone.longcare.model.OrderKey

interface OrderImageRepository {
    suspend fun getImagesByOrderId(orderKey: OrderKey): List<OrderImageEntity>
    suspend fun addImage(
        orderKey: OrderKey,
        imageType: ImageType,
        localUri: String,
        localPath: String? = null
    ): Long
    suspend fun markAsSuccess(imageId: Long, cloudKey: String, cloudUrl: String)
    suspend fun deleteImage(imageId: Long)
    suspend fun deleteImagesByOrderId(orderKey: OrderKey)
}
