package com.ytone.longcare.domain.location

import com.ytone.longcare.common.network.ApiResult

/**
 * 定位相关的数据仓库接口
 */
interface LocationRepository {
    /**
     * 上报地理位置。
     * 接口的参数使用基础类型，将构建Map的逻辑封装在实现层。
     */
    suspend fun addPosition(orderId: Long, latitude: Double, longitude: Double): ApiResult<Unit>
}