package com.ytone.longcare.domain.system

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.AppVersionModel

interface SystemRepository {

    /**
     * 版本检测
     */
    suspend fun checkVersion(): ApiResult<AppVersionModel>
}
