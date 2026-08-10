package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.location.LocationRepository
import com.ytone.longcare.model.AddPositionParamModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : LocationRepository {

    override suspend fun addPosition(orderId: Long, latitude: Double, longitude: Double): ApiResult<Unit> {
        val params =
            AddPositionParamModel(
                orderId = orderId,
                latitude = latitude.toString(),
                longitude = longitude.toString(),
            )
        return apiService.addPosition(params)
    }
}
