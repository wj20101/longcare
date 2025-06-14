package com.ytone.longcare.domain.login

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.LoginPhoneParamModel
import com.ytone.longcare.api.request.SendSmsCodeParamModel
import com.ytone.longcare.api.response.LoginResultModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

interface LoginRepository {
    suspend fun login(mobile: String, code: String): ApiResult<LoginResultModel>
    suspend fun sendSmsCode(mobile: String): ApiResult<Unit>
}

class LoginRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LoginRepository {

    override suspend fun login(mobile: String, code: String) = safeApiCall(ioDispatcher) {
        apiService.phoneLogin(
            LoginPhoneParamModel(
                mobile = mobile, smsCode = code, userIdentity = 1
            )
        )
    }

    override suspend fun sendSmsCode(mobile: String) = safeApiCall(ioDispatcher) {
        apiService.sendSmsCode(SendSmsCodeParamModel(mobile = mobile, codeType = 1))
    }
}