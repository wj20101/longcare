package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.domain.home.HomeDeviceRepository
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import javax.inject.Inject

class HomeDeviceRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : HomeDeviceRepository {
    override suspend fun getDeviceStatus(): ApiResult<ZhiChiDeviceStatusModel> =
        apiService.getZhiChiDeviceStatus()
}
