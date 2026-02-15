package com.ytone.longcare.domain.order

import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.EndOrderResultModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderStateModel
import com.ytone.longcare.api.response.TodayServiceOrderModel
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
     * @param request 订单详情请求参数
     */
    suspend fun getOrderInfo(request: OrderInfoRequestModel): ApiResult<ServiceOrderInfoModel>

    /**
     * 检查订单（NFC签到）
     * @param orderId 订单号
     * @param nfcDeviceId NFC设备ID
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun checkOrder(
        orderId: Long,
        nfcDeviceId: String,
        longitude: String = "",
        latitude: String = ""
    ): ApiResult<Unit>

    /**
     * 工单开始(正式计时)
     * @param orderId 订单号
     * @param selectedProjectIds 选中的项目ID列表
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun starOrder(
        orderId: Long,
        selectedProjectIds: List<Long> = emptyList(),
        longitude: String = "",
        latitude: String = ""
    ): ApiResult<Unit>

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
     * @param centerImgList 服务中图片集合
     * @param endImageList 结束图片集合
     * @param longitude 经度
     * @param latitude 纬度
     * @param endType 结束类型：1=正常结束，2=提前结束
     */
    suspend fun endOrder(
        orderId: Long,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        centerImgList: List<String>,
        endImageList: List<String>,
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ): ApiResult<EndOrderResultModel>

    /**
     * 绑定定位
     * @param orderId 订单号
     * @param nfc NFC设备ID
     * @param longitude 经度
     * @param latitude 纬度
     */
    suspend fun bindLocation(
        orderId: Long,
        nfc: String,
        longitude: String,
        latitude: String
    ): ApiResult<Unit>

    /**
     * 检测结束工单
     * @param orderId 订单号
     * @param projectIdList 完成的服务项目ID集合
     */
    suspend fun checkEndOrder(
        orderId: Long,
        projectIdList: List<Int>
    ): ApiResult<Unit>

    /**
     * 查询服务订单状态
     * @param orderId 订单号
     * @return 订单状态信息
     */
    suspend fun getOrderState(orderId: Long): ApiResult<ServiceOrderStateModel>
}