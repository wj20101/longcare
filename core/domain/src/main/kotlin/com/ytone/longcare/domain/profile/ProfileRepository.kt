package com.ytone.longcare.domain.profile

import com.ytone.longcare.api.response.NurseServiceTimeModel
import com.ytone.longcare.common.network.ApiResult

interface ProfileRepository {

    /**
     * 获取护理员的服务统计数据。
     */
    suspend fun getServiceStatistics(): ApiResult<NurseServiceTimeModel>

    /**
     * 退出登录
     */
    suspend fun logout(): ApiResult<Unit>
}