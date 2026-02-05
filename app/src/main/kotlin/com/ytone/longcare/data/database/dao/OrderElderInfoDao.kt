package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ytone.longcare.data.database.entity.OrderElderInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * 订单老人信息DAO
 */
@Dao
interface OrderElderInfoDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM order_elder_info WHERE order_id = :orderId")
    suspend fun getByOrderId(orderId: Long): OrderElderInfoEntity?
    
    @Query("SELECT * FROM order_elder_info WHERE order_id = :orderId")
    fun observeByOrderId(orderId: Long): Flow<OrderElderInfoEntity?>
    
    @Query("SELECT * FROM order_elder_info WHERE elder_user_id = :elderUserId")
    suspend fun getByElderUserId(elderUserId: Int): List<OrderElderInfoEntity>
    
    // ========== 写入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(elderInfo: OrderElderInfoEntity): Long
    
    @Update
    suspend fun update(elderInfo: OrderElderInfoEntity)
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM order_elder_info WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)
}
