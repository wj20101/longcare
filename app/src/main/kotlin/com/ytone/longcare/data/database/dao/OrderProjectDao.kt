package com.ytone.longcare.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ytone.longcare.data.database.entity.OrderProjectEntity
import kotlinx.coroutines.flow.Flow

/**
 * 订单项目DAO
 */
@Dao
interface OrderProjectDao {
    
    // ========== 查询操作 ==========
    
    @Query("SELECT * FROM order_projects WHERE order_id = :orderId ORDER BY project_id")
    suspend fun getProjectsByOrderId(orderId: Long): List<OrderProjectEntity>
    
    @Query("SELECT * FROM order_projects WHERE order_id = :orderId ORDER BY project_id")
    fun observeProjectsByOrderId(orderId: Long): Flow<List<OrderProjectEntity>>
    
    @Query("SELECT * FROM order_projects WHERE order_id = :orderId AND is_selected = 1 ORDER BY project_id")
    suspend fun getSelectedProjects(orderId: Long): List<OrderProjectEntity>
    
    @Query("SELECT * FROM order_projects WHERE order_id = :orderId AND is_selected = 1 ORDER BY project_id")
    fun observeSelectedProjects(orderId: Long): Flow<List<OrderProjectEntity>>
    
    @Query("SELECT project_id FROM order_projects WHERE order_id = :orderId AND is_selected = 1")
    suspend fun getSelectedProjectIds(orderId: Long): List<Int>
    
    // ========== 写入操作 ==========
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(projects: List<OrderProjectEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(project: OrderProjectEntity): Long
    
    // ========== 更新操作 ==========
    
    @Query("UPDATE order_projects SET is_selected = :isSelected, updated_at = :updatedAt WHERE order_id = :orderId AND project_id = :projectId")
    suspend fun updateSelection(orderId: Long, projectId: Int, isSelected: Boolean, updatedAt: Long = System.currentTimeMillis())
    
    @Query("UPDATE order_projects SET is_selected = 0 WHERE order_id = :orderId")
    suspend fun clearAllSelections(orderId: Long)
    
    /**
     * 批量更新选中的项目
     * 先清除所有选中状态，再设置新的选中项目
     */
    @Transaction
    suspend fun updateSelectedProjects(orderId: Long, selectedProjectIds: List<Int>) {
        clearAllSelections(orderId)
        selectedProjectIds.forEach { projectId ->
            updateSelection(orderId, projectId, true)
        }
    }
    
    // ========== 删除操作 ==========
    
    @Query("DELETE FROM order_projects WHERE order_id = :orderId")
    suspend fun deleteByOrderId(orderId: Long)
    
    @Query("DELETE FROM order_projects WHERE order_id = :orderId AND project_id = :projectId")
    suspend fun deleteProject(orderId: Long, projectId: Int)
}
