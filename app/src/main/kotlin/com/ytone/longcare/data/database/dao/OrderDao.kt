package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.ytone.longcare.data.database.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

/**
 * 订单核心信息DAO
 */
@Dao
interface OrderDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM orders WHERE order_id = :orderId")
    suspend fun getOrderById(orderId: Long): OrderEntity?
    
    @Query("SELECT * FROM orders WHERE order_id = :orderId")
    fun observeOrderById(orderId: Long): Flow<OrderEntity?>
    
    @Query("SELECT * FROM orders ORDER BY updated_at DESC")
    fun observeAllOrders(): Flow<List<OrderEntity>>
    
    @Query("SELECT * FROM orders WHERE state = :state ORDER BY updated_at DESC")
    fun observeOrdersByState(state: Int): Flow<List<OrderEntity>>
    
    // ========== 写入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(order: OrderEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(orders: List<OrderEntity>)
    
    @Update
    suspend fun update(order: OrderEntity)

    @Transaction
    suspend fun insertOrUpdate(order: OrderEntity): Long {
        val id = insertIgnore(order)
        if (id == -1L) {
            update(order)
            return order.orderId
        }
        return id
    }
    
    // ========== 更新操作 ==========
    
    @Query("UPDATE orders SET state = :state, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateState(orderId: Long, state: Int, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE orders SET last_sync_time = :syncTime, updated_at = :updatedAt WHERE order_id = :orderId")
    suspend fun updateSyncTime(orderId: Long, syncTime: Long, updatedAt: Long = System.currentTimeMillis())
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM orders WHERE order_id = :orderId")
    suspend fun deleteById(orderId: Long)
    
    @Query("DELETE FROM orders")
    suspend fun deleteAll()
    
    // ========== 检查操作 ==========
    
    @Query("SELECT EXISTS(SELECT 1 FROM orders WHERE order_id = :orderId)")
    suspend fun exists(orderId: Long): Boolean
}
