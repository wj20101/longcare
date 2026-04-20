package com.ytone.longcare.domain.login

import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.StartConfigResultModel

interface LoginRepository {
    suspend fun login(mobile: String, code: String): ApiResult<LoginResultModel>
    suspend fun sendSmsCode(mobile: String): ApiResult<Unit>
    suspend fun recordLoginLog(
        phoneSystem: String,
        phoneVersion: String,
        networkType: String,
        networkOperator: String,
    )
    suspend fun getStartConfig(): ApiResult<StartConfigResultModel>
}
