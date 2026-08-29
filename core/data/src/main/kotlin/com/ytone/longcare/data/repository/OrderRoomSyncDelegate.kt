package com.ytone.longcare.data.repository

import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.database.entity.toDb
import com.ytone.longcare.data.database.entity.toModel
import com.ytone.longcare.data.userstorage.UserDatabaseAccess
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.data.repository.OrderMapper.toOrderElderInfoEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderProjectEntities
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.ServiceOrderInfoModel

internal class OrderRoomSyncDelegate(
    private val databaseAccess: UserDatabaseAccess,
) {
    suspend fun syncOrderInfoToRoom(
        lease: UserStorageLease,
        orderId: Long,
        orderInfo: ServiceOrderInfoModel,
    ) = databaseAccess.withLease(lease) { database, _ ->
        database.syncOrderInfo(orderId, orderInfo)
    }

    suspend fun loadOrderWithDetails(
        lease: UserStorageLease,
        orderId: Long,
    ): OrderWithDetails? = databaseAccess.withLease(lease) { database, _ ->
        database.loadOrderWithDetails(orderId)
    }

    suspend fun persistOrderInfoAndBuildDetails(
        lease: UserStorageLease,
        orderId: Long,
        orderInfo: ServiceOrderInfoModel,
    ): OrderWithDetails = databaseAccess.withLease(lease) { database, _ ->
        database.syncOrderInfo(orderId, orderInfo)
        database.loadOrderWithDetails(orderId) ?: OrderWithDetails(
            order = orderInfo.toOrderEntity(orderId),
            elderInfo = orderInfo.userInfo?.toOrderElderInfoEntity(orderId),
            localState = OrderLocalStateEntity(orderId = orderId),
            projects = orderInfo.projectList?.toOrderProjectEntities(orderId) ?: emptyList(),
        )
    }

    private suspend fun LongCareDatabase.syncOrderInfo(
        orderId: Long,
        orderInfo: ServiceOrderInfoModel,
    ) {
        orderDao().insertOrUpdate(orderInfo.toOrderEntity(orderId).toDb())

        orderInfo.userInfo?.toOrderElderInfoEntity(orderId)?.toDb()?.let { elderInfo ->
            orderElderInfoDao().insertOrUpdate(elderInfo)
        }

        val projectEntities = orderInfo.projectList?.toOrderProjectEntities(orderId).orEmpty()
        if (projectEntities.isNotEmpty()) {
            val existingSelections = orderProjectDao().getSelectedProjectIds(orderId).toSet()
            val updatedProjects = projectEntities.map { project ->
                project.copy(isSelected = project.projectId in existingSelections).toDb()
            }
            orderProjectDao().deleteByOrderId(orderId)
            orderProjectDao().insertOrUpdateAll(updatedProjects)
        }

        if (orderLocalStateDao().getByOrderId(orderId) == null) {
            orderLocalStateDao().insertOrUpdate(OrderLocalStateEntity(orderId = orderId).toDb())
        }
    }

    private suspend fun LongCareDatabase.loadOrderWithDetails(orderId: Long): OrderWithDetails? {
        val cachedOrder = orderDao().getOrderById(orderId) ?: return null
        val elderInfo = orderElderInfoDao().getByOrderId(orderId)
        val localState = orderLocalStateDao().getByOrderId(orderId)
        val projects = orderProjectDao().getProjectsByOrderId(orderId)
        return OrderWithDetails(
            order = cachedOrder.toModel(),
            elderInfo = elderInfo?.toModel(),
            localState = localState?.toModel(),
            projects = projects.map { it.toModel() },
        )
    }
}
