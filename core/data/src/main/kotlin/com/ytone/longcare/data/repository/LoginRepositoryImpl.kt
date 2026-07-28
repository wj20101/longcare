package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.LoginPhoneParamModel
import com.ytone.longcare.model.SendSmsCodeParamModel
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class LoginRepositoryImpl @Inject constructor(
    private val apiService: LongCareApiService,
) : LoginRepository {

    override suspend fun login(mobile: String, code: String) =
        apiService.phoneLogin(
            LoginPhoneParamModel(
                mobile = mobile, smsCode = code, userIdentity = 1
            )
        )

    override suspend fun sendSmsCode(mobile: String) =
        apiService.sendSmsCode(SendSmsCodeParamModel(mobile = mobile, codeType = 1))

    override suspend fun recordLoginLog(
        phoneSystem: String,
        phoneVersion: String,
        networkType: String,
        networkOperator: String,
    ) {
        try {
            apiService.recordLoginLog(
                LoginLogParamModel(
                    phoneSystem = phoneSystem,
                    phoneVersion = phoneVersion,
                    networkType = networkType,
                    networkOperator = networkOperator,
                ),
            )
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
        }
    }

    override suspend fun getStartConfig() = apiService.getStartConfig()
}
