package com.ytone.longcare.domain.order

import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.common.network.ApiResult

/**
 * 订单相关的数据仓库接口
 */
interface OrderRepository {
    /**
     * 获取当天的服务订单列表
     */
    suspend fun getTodayOrderList(): ApiResult<List<TodayServiceOrderModel>>
    
    /**
     * 按天查询服务订单
     * @param daytime 查询日期，格式例如: "yyyy-MM-dd"
     */
    suspend fun getOrderList(daytime: String): ApiResult<List<ServiceOrderModel>>
}