package com.ytone.longcare.domain.system

import com.ytone.longcare.api.response.AppVersionModel
import com.ytone.longcare.common.network.ApiResult

interface SystemRepository {

    /**
     * 版本检测
     */
    suspend fun checkVersion(): ApiResult<AppVersionModel>
}