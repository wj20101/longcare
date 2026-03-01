package com.ytone.longcare.data.repository

import com.ytone.longcare.data.database.dao.OrderDao
import com.ytone.longcare.data.database.dao.OrderElderInfoDao
import com.ytone.longcare.data.database.dao.OrderLocalStateDao
import com.ytone.longcare.data.database.dao.OrderProjectDao
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.data.repository.OrderMapper.toOrderElderInfoEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderProjectEntities
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel

internal class OrderRoomSyncDelegate(
    private val orderDao: OrderDao,
    private val orderElderInfoDao: OrderElderInfoDao,
    private val orderLocalStateDao: OrderLocalStateDao,
    private val orderProjectDao: OrderProjectDao
) {
    suspend fun syncOrderInfoToRoom(orderId: Long, orderInfo: ServiceOrderInfoModel) {
        orderDao.insertOrUpdate(orderInfo.toOrderEntity(orderId).toDb())

        val elderInfoEntity = orderInfo.userInfo?.toOrderElderInfoEntity(orderId)?.toDb()
        if (elderInfoEntity != null) {
            orderElderInfoDao.insertOrUpdate(elderInfoEntity)
        }

        val projectEntities = orderInfo.projectList?.toOrderProjectEntities(orderId) ?: emptyList()
        if (projectEntities.isNotEmpty()) {
            val existingSelections = orderProjectDao.getSelectedProjectIds(orderId).toSet()
            val updatedProjects = projectEntities.map { project ->
                project.copy(isSelected = existingSelections.contains(project.projectId))
            }.map { it.toDb() }
            orderProjectDao.deleteByOrderId(orderId)
            orderProjectDao.insertOrUpdateAll(updatedProjects)
        }

        if (orderLocalStateDao.getByOrderId(orderId) == null) {
            orderLocalStateDao.insertOrUpdate(OrderLocalStateEntity(orderId = orderId).toDb())
        }
    }

    suspend fun loadOrderWithDetails(orderId: Long): OrderWithDetails? {
        val cachedOrder = orderDao.getOrderById(orderId) ?: return null
        val elderInfo = orderElderInfoDao.getByOrderId(orderId)
        val localState = orderLocalStateDao.getByOrderId(orderId)
        val projects = orderProjectDao.getProjectsByOrderId(orderId)
        return OrderWithDetails(
            order = cachedOrder.toModel(),
            elderInfo = elderInfo?.toModel(),
            localState = localState?.toModel(),
            projects = projects.map { it.toModel() }
        )
    }

    suspend fun persistOrderInfoAndBuildDetails(
        orderId: Long,
        orderInfo: ServiceOrderInfoModel
    ): OrderWithDetails {
        syncOrderInfoToRoom(orderId, orderInfo)
        return loadOrderWithDetails(orderId) ?: OrderWithDetails(
            order = orderInfo.toOrderEntity(orderId),
            elderInfo = orderInfo.userInfo?.toOrderElderInfoEntity(orderId),
            localState = OrderLocalStateEntity(orderId = orderId),
            projects = orderInfo.projectList?.toOrderProjectEntities(orderId) ?: emptyList()
        )
    }
}
