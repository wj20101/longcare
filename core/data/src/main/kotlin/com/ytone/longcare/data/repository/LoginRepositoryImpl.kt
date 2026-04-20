package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.LoginPhoneParamModel
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.Response
import com.ytone.longcare.model.SendSmsCodeParamModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.safeApiCall
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.login.LoginRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException
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

    override suspend fun recordLoginLog(
        phoneSystem: String,
        phoneVersion: String,
        networkType: String,
        networkOperator: String,
    ) = withContext(ioDispatcher) {
        try {
            val response = apiService.recordLoginLog(
                LoginLogParamModel(
                    phoneSystem = phoneSystem,
                    phoneVersion = phoneVersion,
                    networkType = networkType,
                    networkOperator = networkOperator,
                ),
            )
            response.toSilentUnitResult()
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            Unit
        }
    }

    override suspend fun getStartConfig() = safeApiCall(ioDispatcher, eventBus) {
        apiService.getStartConfig()
    }
}

private fun Response<Unit>.toSilentUnitResult() {
    if (!isSuccess()) {
        return
    }
}
