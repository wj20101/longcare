package com.ytone.longcare.domain.order

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
     * 获取服务中的订单列表
     */
    suspend fun getInOrderList(): ApiResult<List<ServiceOrderModel>>

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
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun startOrder(
        orderId: Long, 
        nfcDeviceId: String,
        longitude: String = "",
        latitude: String = ""
    ): ApiResult<Unit>

    /**
     * 工单开始(正式计时)
     * @param orderId 订单号
     */
    suspend fun starOrder(orderId: Long): ApiResult<Unit>

    /**
     * 添加开始老人照片
     * @param orderId 订单号
     * @param userImgList 用户图片集合
     */
    suspend fun upUserStartImg(orderId: Long, userImgList: List<String>): ApiResult<Unit>

    /**
     * 结束订单服务（NFC签退）
     * @param orderId 订单号
     * @param nfcDeviceId NFC设备ID
     * @param projectIdList 完成的服务项目ID集合
     * @param beginImgList 开始图片集合
     * @param endImageList 结束图片集合
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun endOrder(
        orderId: Long,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        longitude: String = "",
        latitude: String = ""
    ): ApiResult<Unit>
}