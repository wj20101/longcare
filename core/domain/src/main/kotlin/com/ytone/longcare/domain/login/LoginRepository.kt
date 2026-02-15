package com.ytone.longcare.domain.login

import com.ytone.longcare.api.response.LoginResultModel
import com.ytone.longcare.api.response.StartConfigResultModel
import com.ytone.longcare.common.network.ApiResult

interface LoginRepository {
    suspend fun login(mobile: String, code: String): ApiResult<LoginResultModel>
    suspend fun sendSmsCode(mobile: String): ApiResult<Unit>
    suspend fun getStartConfig(): ApiResult<StartConfigResultModel>
}
