package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.CheckOrderParamModel
import com.ytone.longcare.model.EndOrderParamModel
import com.ytone.longcare.model.OrderListParamModel
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.StarOrderParamModel
import com.ytone.longcare.model.UpUserStartImgParamModel
import com.ytone.longcare.model.BindLocationParamModel
import com.ytone.longcare.model.CheckEndOrderParamModel
import com.ytone.longcare.model.OrderStateParamModel
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.EndOrderResultModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.order.OrderRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : OrderRepository {

    override suspend fun getTodayOrderList(): ApiResult<List<TodayServiceOrderModel>> =
        apiService.getTodayOrderList()

    override suspend fun getInOrderList(): ApiResult<List<ServiceOrderModel>> =
        apiService.getInOrderList()

    override suspend fun getOrderList(daytime: String): ApiResult<List<ServiceOrderModel>> =
        apiService.getOrderList(OrderListParamModel(daytime = daytime))

    override suspend fun getOrderInfo(orderKey: OrderKey): ApiResult<ServiceOrderInfoModel> =
        apiService.getOrderInfo(
            OrderInfoParamModel(
                orderId = orderKey.orderId,
                planId = orderKey.planId,
            )
        )

    override suspend fun checkOrder(
        orderId: Long,
        nfcDeviceId: String,
        longitude: String,
        latitude: String
    ): ApiResult<Unit> =
        apiService.checkOrder(
            CheckOrderParamModel(
                orderId = orderId,
                nfc = nfcDeviceId,
                longitude = longitude,
                latitude = latitude,
            )
        )

    override suspend fun starOrder(
        orderId: Long, 
        selectedProjectIds: List<Long>,
        longitude: String,
        latitude: String
    ): ApiResult<Unit> =
        apiService.starOrder(
            StarOrderParamModel(
                orderId = orderId,
                porjectIdList = selectedProjectIds.map { it.toInt() },
                longitude = longitude,
                latitude = latitude,
            )
        )

    override suspend fun upUserStartImg(
        orderId: Long,
        userImgList: List<String>,
    ): ApiResult<Unit> =
        apiService.upUserStartImg(
            UpUserStartImgParamModel(
                orderId = orderId,
                userImgList = userImgList,
            )
        )

    override suspend fun endOrder(
        orderId: Long,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        centerImgList: List<String>,
        endImageList: List<String>,
        longitude: String,
        latitude: String,
        endType: Int
    ): ApiResult<EndOrderResultModel> =
        apiService.endOrder(
            EndOrderParamModel(
                orderId = orderId,
                nfc = nfcDeviceId,
                longitude = longitude,
                latitude = latitude,
                porjectIdList = projectIdList,
                beginImgList = beginImgList,
                centerImgList = centerImgList,
                endImgList = endImageList,
                endType = endType,
            )
        )

    override suspend fun bindLocation(
        orderId: Long,
        nfc: String,
        longitude: String,
        latitude: String
    ): ApiResult<Unit> =
        apiService.bindLocation(
            BindLocationParamModel(
                orderId = orderId,
                nfc = nfc,
                longitude = longitude,
                latitude = latitude,
            )
        )

    override suspend fun checkEndOrder(
        orderId: Long,
        projectIdList: List<Int>
    ): ApiResult<Unit> =
        apiService.checkEndOrder(
            CheckEndOrderParamModel(
                orderid = orderId,
                porjectIdList = projectIdList,
            )
        )

    override suspend fun getOrderState(orderId: Long): ApiResult<ServiceOrderStateModel> =
        apiService.getOrderState(OrderStateParamModel(orderId = orderId))
}
