package com.ytone.longcare.domain.location

import com.ytone.longcare.model.OrderLocationEntity

/**
 * 定位上报队列仓库。
 * 负责管理本地定位上报记录的入队、读取、状态更新与清理。
 */
interface LocationUploadQueueRepository {
    suspend fun insert(location: OrderLocationEntity): Long

    suspend fun getUploadQueue(statuses: List<Int>, limit: Int): List<OrderLocationEntity>

    suspend fun updateStatus(id: Long, status: Int)

    suspend fun deleteByStatusBefore(status: Int, beforeTime: Long): Int
}
