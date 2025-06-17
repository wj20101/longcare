package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.OrderListParamModel
import com.ytone.longcare.api.request.OrderInfoParamModel
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.order.OrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OrderRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus,
) : OrderRepository {

    override suspend fun getTodayOrderList(): ApiResult<List<TodayServiceOrderModel>> {
        return safeApiCall(ioDispatcher, eventBus) { apiService.getTodayOrderList() }
    }
    
    override suspend fun getOrderList(daytime: String): ApiResult<List<ServiceOrderModel>> {
        return safeApiCall(ioDispatcher, eventBus) { 
            apiService.getOrderList(OrderListParamModel(daytime = daytime))
        }
    }
    
    override suspend fun getOrderInfo(orderId: Long): ApiResult<ServiceOrderInfoModel> {
        return safeApiCall(ioDispatcher, eventBus) {
            apiService.getOrderInfo(OrderInfoParamModel(orderId = orderId))
        }
    }
}