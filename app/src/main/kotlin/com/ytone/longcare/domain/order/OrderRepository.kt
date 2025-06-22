package com.ytone.longcare.domain.order

import com.ytone.longcare.api.request.EndOrderParamModel
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
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
    
    /**
     * 获取服务订单详情
     * @param orderId 订单号
     */
    suspend fun getOrderInfo(orderId: Long): ApiResult<ServiceOrderInfoModel>
    
    /**
     * 开始订单服务（NFC签到）
     * @param orderId 订单号
     * @param nfcDeviceId NFC设备ID
     */
    suspend fun startOrder(orderId: Long, nfcDeviceId: String): ApiResult<Unit>

    /**
     * 结束订单服务（NFC签退）
     * @param params 结束订单所需的参数
     */
    suspend fun endOrder(params: EndOrderParamModel): ApiResult<Unit>
}