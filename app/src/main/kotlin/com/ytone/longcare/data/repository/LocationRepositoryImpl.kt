package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.AddPositionParamModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.location.LocationRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    private val eventBus: AppEventBus,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LocationRepository {

    override suspend fun addPosition(orderId: Long, latitude: Double, longitude: Double): ApiResult<Unit> {
        return safeApiCall(ioDispatcher, eventBus) {
            val params = AddPositionParamModel(orderId = orderId, latitude = latitude.toString(), longitude = longitude.toString())
            // 注意：由于 addPosition 现在返回 ApiResponse，我们需要解包
            apiService.addPosition(params)
        }
    }
}