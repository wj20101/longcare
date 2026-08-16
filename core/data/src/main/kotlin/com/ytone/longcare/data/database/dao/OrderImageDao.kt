package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ytone.longcare.data.database.entity.OrderImageEntityDb
import kotlinx.coroutines.flow.Flow

/**
 * 订单图片DAO
 */
@Dao
interface OrderImageDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM order_images WHERE order_id = :orderId ORDER BY created_at")
    suspend fun getImagesByOrderId(orderId: Long): List<OrderImageEntityDb>
    
    @Query("SELECT * FROM order_images WHERE order_id = :orderId ORDER BY created_at")
    fun observeImagesByOrderId(orderId: Long): Flow<List<OrderImageEntityDb>>
    
    @Query("SELECT * FROM order_images WHERE order_id = :orderId AND image_type = :imageType ORDER BY created_at")
    suspend fun getImagesByType(orderId: Long, imageType: Int): List<OrderImageEntityDb>
    
    @Query("SELECT * FROM order_images WHERE order_id = :orderId AND image_type = :imageType ORDER BY created_at")
    fun observeImagesByType(orderId: Long, imageType: Int): Flow<List<OrderImageEntityDb>>
    
    @Query("SELECT * FROM order_images WHERE order_id = :orderId AND upload_status = :status")
    suspend fun getImagesByStatus(orderId: Long, status: Int): List<OrderImageEntityDb>
    
    @Query("SELECT * FROM order_images WHERE upload_status = :status")
    suspend fun getAllImagesByStatus(status: Int): List<OrderImageEntityDb>
    
    @Query("SELECT * FROM order_images WHERE id = :id")
    suspend fun getById(id: Long): OrderImageEntityDb?
    
    // ========== 写入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(image: OrderImageEntityDb): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<OrderImageEntityDb>)
    
    @Update
    suspend fun update(image: OrderImageEntityDb)
    
    // ========== 更新操作 ==========
    
    @Query("UPDATE order_images SET upload_status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_images SET upload_status = :status, cloud_key = :cloudKey, cloud_url = NULL, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateUploadSuccess(id: Long, status: Int, cloudKey: String, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_images SET upload_status = :status, error_message = :errorMessage, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateUploadFailed(id: Long, status: Int, errorMessage: String, updatedAt: Long = System.currentTimeMillis())
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM order_images WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("DELETE FROM order_images WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)
    
    @Query("DELETE FROM order_images WHERE order_id = :orderId AND image_type = :imageType")
    suspend fun deleteByType(orderId: Long, imageType: Int)
    
    // ========== 统计操作 ==========
    
    @Query("SELECT COUNT(*) FROM order_images WHERE order_id = :orderId AND upload_status = :status")
    suspend fun countByStatus(orderId: Long, status: Int): Int
}
