package com.ytone.longcare.data.repository

import com.ytone.longcare.data.database.dao.OrderImageDao
import com.ytone.longcare.data.database.entity.ImageType
import com.ytone.longcare.data.database.entity.ImageUploadStatus
import com.ytone.longcare.data.database.entity.OrderImageEntity
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片仓库
 * 
 * 统一管理订单图片的操作：
 * 1. 图片添加和删除
 * 2. 上传状态管理
 * 3. 查询和观察
 */
@Singleton
class ImageRepository @Inject constructor(
    private val orderImageDao: OrderImageDao
) {
    
    // ========== 查询操作 ==========
    
    /**
     * 获取订单所有图片
     * @param orderKey 订单标识符
     */
    suspend fun getImagesByOrderId(orderKey: OrderKey): List<OrderImageEntity> {
        return orderImageDao.getImagesByOrderId(orderKey.orderId)
    }
    
    /**
     * 观察订单所有图片
     * @param orderKey 订单标识符
     */
    fun observeImagesByOrderId(orderKey: OrderKey): Flow<List<OrderImageEntity>> {
        return orderImageDao.observeImagesByOrderId(orderKey.orderId)
    }
    
    /**
     * 获取指定类型的图片
     * @param orderKey 订单标识符
     * @param imageType 图片类型
     */
    suspend fun getImagesByType(orderKey: OrderKey, imageType: ImageType): List<OrderImageEntity> {
        return orderImageDao.getImagesByType(orderKey.orderId, imageType.value)
    }
    
    /**
     * 观察指定类型的图片
     * @param orderKey 订单标识符
     * @param imageType 图片类型
     */
    fun observeImagesByType(orderKey: OrderKey, imageType: ImageType): Flow<List<OrderImageEntity>> {
        return orderImageDao.observeImagesByType(orderKey.orderId, imageType.value)
    }
    
    /**
     * 获取待上传的图片
     * @param orderKey 订单标识符
     */
    suspend fun getPendingImages(orderKey: OrderKey): List<OrderImageEntity> {
        return orderImageDao.getImagesByStatus(orderKey.orderId, ImageUploadStatus.PENDING.value)
    }
    
    /**
     * 获取所有待上传的图片（跨订单）
     */
    suspend fun getAllPendingImages(): List<OrderImageEntity> {
        return orderImageDao.getAllImagesByStatus(ImageUploadStatus.PENDING.value)
    }
    
    /**
     * 获取失败的图片
     * @param orderKey 订单标识符
     */
    suspend fun getFailedImages(orderKey: OrderKey): List<OrderImageEntity> {
        return orderImageDao.getImagesByStatus(orderKey.orderId, ImageUploadStatus.FAILED.value)
    }
    
    // ========== 写入操作 ==========
    
    /**
     * 添加图片
     * @param orderKey 订单标识符
     * @param imageType 图片类型
     * @param localUri 本地URI
     * @param localPath 本地路径
     */
    suspend fun addImage(
        orderKey: OrderKey,
        imageType: ImageType,
        localUri: String,
        localPath: String? = null
    ): Long {
        val entity = OrderImageEntity(
            orderId = orderKey.orderId,
            imageType = imageType.value,
            localUri = localUri,
            localPath = localPath,
            uploadStatus = ImageUploadStatus.PENDING.value
        )
        return orderImageDao.insert(entity)
    }
    
    /**
     * 批量添加图片
     * @param orderKey 订单标识符
     * @param imageType 图片类型
     * @param localUris 本地URI列表
     */
    suspend fun addImages(
        orderKey: OrderKey,
        imageType: ImageType,
        localUris: List<String>
    ) {
        val entities = localUris.map { uri ->
            OrderImageEntity(
                orderId = orderKey.orderId,
                imageType = imageType.value,
                localUri = uri,
                uploadStatus = ImageUploadStatus.PENDING.value
            )
        }
        orderImageDao.insertAll(entities)
    }
    
    // ========== 状态更新 ==========
    
    /**
     * 标记为上传中
     */
    suspend fun markAsUploading(imageId: Long) {
        orderImageDao.updateStatus(imageId, ImageUploadStatus.UPLOADING.value)
    }
    
    /**
     * 标记上传成功
     */
    suspend fun markAsSuccess(imageId: Long, cloudKey: String, cloudUrl: String) {
        orderImageDao.updateUploadSuccess(
            id = imageId,
            status = ImageUploadStatus.SUCCESS.value,
            cloudKey = cloudKey,
            cloudUrl = cloudUrl
        )
    }
    
    /**
     * 标记上传失败
     */
    suspend fun markAsFailed(imageId: Long, errorMessage: String) {
        orderImageDao.updateUploadFailed(
            id = imageId,
            status = ImageUploadStatus.FAILED.value,
            errorMessage = errorMessage
        )
    }
    
    /**
     * 重置为待上传（用于重试）
     */
    suspend fun resetToPending(imageId: Long) {
        orderImageDao.updateStatus(imageId, ImageUploadStatus.PENDING.value)
    }

    /**
     * 更新状态（通用）
     */
    suspend fun updateStatus(imageId: Long, status: ImageUploadStatus) {
        orderImageDao.updateStatus(imageId, status.value)
    }
    
    // ========== 删除操作 ==========
    
    /**
     * 删除单张图片
     */
    suspend fun deleteImage(imageId: Long) {
        orderImageDao.deleteById(imageId)
    }
    
    /**
     * 删除订单所有图片
     * @param orderKey 订单标识符
     */
    suspend fun deleteImagesByOrderId(orderKey: OrderKey) {
        orderImageDao.deleteByOrderId(orderKey.orderId)
    }
    
    /**
     * 删除指定类型的图片
     * @param orderKey 订单标识符
     * @param imageType 图片类型
     */
    suspend fun deleteImagesByType(orderKey: OrderKey, imageType: ImageType) {
        orderImageDao.deleteByType(orderKey.orderId, imageType.value)
    }
    
    // ========== 统计操作 ==========
    
    /**
     * 获取待上传图片数量
     * @param orderKey 订单标识符
     */
    suspend fun countPendingImages(orderKey: OrderKey): Int {
        return orderImageDao.countByStatus(orderKey.orderId, ImageUploadStatus.PENDING.value)
    }
    
    /**
     * 获取上传成功图片数量
     * @param orderKey 订单标识符
     */
    suspend fun countSuccessImages(orderKey: OrderKey): Int {
        return orderImageDao.countByStatus(orderKey.orderId, ImageUploadStatus.SUCCESS.value)
    }
    
    /**
     * 获取上传失败图片数量
     * @param orderKey 订单标识符
     */
    suspend fun countFailedImages(orderKey: OrderKey): Int {
        return orderImageDao.countByStatus(orderKey.orderId, ImageUploadStatus.FAILED.value)
    }
    
    // ========== 便捷方法 ==========
    
    /**
     * 获取已上传成功的图片URL列表（按类型分组）
     * @param orderKey 订单标识符
     */
    suspend fun getUploadedImageUrls(orderKey: OrderKey): Map<ImageType, List<String>> {
        val successImages = orderImageDao.getImagesByStatus(orderKey.orderId, ImageUploadStatus.SUCCESS.value)
        return successImages
            .filter { it.cloudUrl != null }
            .groupBy { it.getImageTypeEnum() }
            .mapValues { (_, images) -> images.mapNotNull { it.cloudUrl } }
    }
    
    /**
     * 获取护理前照片URL列表
     * @param orderKey 订单标识符
     */
    suspend fun getBeforeCareImageUrls(orderKey: OrderKey): List<String> {
        return getUploadedImageUrls(orderKey)[ImageType.BEFORE_CARE] ?: emptyList()
    }
    
    /**
     * 获取护理中照片URL列表
     * @param orderKey 订单标识符
     */
    suspend fun getCenterCareImageUrls(orderKey: OrderKey): List<String> {
        return getUploadedImageUrls(orderKey)[ImageType.CENTER_CARE] ?: emptyList()
    }
    
    /**
     * 获取护理后照片URL列表
     * @param orderKey 订单标识符
     */
    suspend fun getAfterCareImageUrls(orderKey: OrderKey): List<String> {
        return getUploadedImageUrls(orderKey)[ImageType.AFTER_CARE] ?: emptyList()
    }
}
