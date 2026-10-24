package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ytone.longcare.data.database.entity.OrderLocalStateEntityDb
import kotlinx.coroutines.flow.Flow

/**
 * 订单本地状态DAO
 */
@Dao
interface OrderLocalStateDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM order_local_states WHERE order_id = :orderId")
    suspend fun getByOrderId(orderId: Long): OrderLocalStateEntityDb?
    
    @Query("SELECT * FROM order_local_states WHERE order_id = :orderId")
    fun observeByOrderId(orderId: Long): Flow<OrderLocalStateEntityDb?>
    
    @Query("SELECT * FROM order_local_states WHERE local_status = :status ORDER BY updated_at DESC")
    fun observeByStatus(status: Int): Flow<List<OrderLocalStateEntityDb>>
    
    @Query("SELECT * FROM order_local_states WHERE needs_sync = 1")
    suspend fun getUnsynced(): List<OrderLocalStateEntityDb>
    
    // ========== 写入操作 ==========
    
    // 详情同步与正式开始可能同时初始化；后到的默认记录不能清空既有服务进度。
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(localState: OrderLocalStateEntityDb): Long
    
    @Update
    suspend fun update(localState: OrderLocalStateEntityDb)
    
    // ========== 更新操作 ==========
    
    @Query("UPDATE order_local_states SET local_status = :status, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateStatus(orderId: Long, status: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_local_states SET local_start_timestamp = :timestamp, local_start_elapsed_realtime = :elapsedRealtime, local_start_boot_count = :bootCount, local_status = 1, updated_at = :updatedAt WHERE order_id = :orderId AND local_start_timestamp IS NULL AND local_status != 2")
    suspend fun startService(
        orderId: Long,
        timestamp: Long,
        elapsedRealtime: Long?,
        bootCount: Int?,
        updatedAt: Long = System.currentTimeMillis(),
    )
    
    @Query("UPDATE order_local_states SET local_end_timestamp = :timestamp, local_status = 2, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun endService(orderId: Long, timestamp: Long, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_local_states SET face_verification_completed = :completed, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateFaceVerification(orderId: Long, completed: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_local_states SET needs_sync = :needsSync, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateNeedsSync(orderId: Long, needsSync: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM order_local_states WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)
}
