package com.ytone.longcare.domain.home

import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult

interface HomeDeviceRepository {
    suspend fun getDeviceStatus(): ApiResult<ZhiChiDeviceStatusModel>
}
