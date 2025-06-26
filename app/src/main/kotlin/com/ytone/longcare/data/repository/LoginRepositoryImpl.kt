package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.request.LoginPhoneParamModel
import com.ytone.longcare.api.request.SendSmsCodeParamModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.login.LoginRepository
import kotlinx.coroutines.CoroutineDispatcher
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) : LoginRepository {

    override suspend fun login(mobile: String, code: String) = safeApiCall(ioDispatcher, eventBus) {
        apiService.phoneLogin(
            LoginPhoneParamModel(
                mobile = mobile, smsCode = code, userIdentity = 1
            )
        )
    }

    override suspend fun sendSmsCode(mobile: String) = safeApiCall(ioDispatcher, eventBus) {
        apiService.sendSmsCode(SendSmsCodeParamModel(mobile = mobile, codeType = 1))
    }
}