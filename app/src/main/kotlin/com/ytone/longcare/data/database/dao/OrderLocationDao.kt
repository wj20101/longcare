package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ytone.longcare.data.database.entity.OrderLocationEntity
import kotlinx.coroutines.flow.Flow

/**
 * 订单定位DAO
 */
@Dao
interface OrderLocationDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM order_locations WHERE order_id = :orderId ORDER BY timestamp DESC")
    suspend fun getLocationsByOrderId(orderId: Long): List<OrderLocationEntity>
    
    @Query("SELECT * FROM order_locations WHERE order_id = :orderId ORDER BY timestamp DESC")
    fun observeLocationsByOrderId(orderId: Long): Flow<List<OrderLocationEntity>>
    
    @Query("SELECT * FROM order_locations WHERE order_id = :orderId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestLocation(orderId: Long): OrderLocationEntity?
    
    @Query("SELECT * FROM order_locations WHERE upload_status = :status")
    suspend fun getLocationsByStatus(status: Int): List<OrderLocationEntity>
    
    // ========== 写入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: OrderLocationEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<OrderLocationEntity>)
    
    // ========== 更新操作 ==========
    
    @Query("UPDATE order_locations SET upload_status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: Int)
    
    @Query("UPDATE order_locations SET upload_status = :newStatus WHERE id IN (:ids)")
    suspend fun batchUpdateStatus(ids: List<Long>, newStatus: Int)
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM order_locations WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)
    
    @Query("DELETE FROM order_locations WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    // ========== 统计操作 ==========
    
    @Query("SELECT COUNT(*) FROM order_locations WHERE order_id = :orderId")
    suspend fun countByOrderId(orderId: Long): Int
    
    @Query("SELECT COUNT(*) FROM order_locations WHERE order_id = :orderId AND upload_status = :status")
    suspend fun countByStatus(orderId: Long, status: Int): Int
}
