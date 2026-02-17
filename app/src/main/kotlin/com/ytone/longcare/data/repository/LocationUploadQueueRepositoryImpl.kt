package com.ytone.longcare.data.repository

import com.ytone.longcare.data.database.dao.OrderLocationDao
import com.ytone.longcare.model.OrderLocationEntity
import com.ytone.longcare.domain.location.LocationUploadQueueRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationUploadQueueRepositoryImpl @Inject constructor(
    private val orderLocationDao: OrderLocationDao
) : LocationUploadQueueRepository {

    override suspend fun insert(location: OrderLocationEntity): Long {
        return orderLocationDao.insert(location)
    }

    override suspend fun getUploadQueue(statuses: List<Int>, limit: Int): List<OrderLocationEntity> {
        return orderLocationDao.getUploadQueue(statuses = statuses, limit = limit)
    }

    override suspend fun updateStatus(id: Long, status: Int) {
        orderLocationDao.updateStatus(id = id, status = status)
    }

    override suspend fun deleteByStatusBefore(status: Int, beforeTime: Long): Int {
        return orderLocationDao.deleteByStatusBefore(status = status, beforeTime = beforeTime)
    }
}
