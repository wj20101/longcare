package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.Response
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class LoginRepositoryImplTest {

    @Test
    fun `recordLoginLog delegates to api service`() = runTest(StandardTestDispatcher()) {
        var delegatedCallCount = 0
        var delegatedParam: LoginLogParamModel? = null
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java)
        ) { _, method, args ->
            when (method.name) {
                "recordLoginLog" -> {
                    delegatedCallCount += 1
                    delegatedParam = args?.firstOrNull() as? LoginLogParamModel
                    Response(resultCode = 1000, resultMsg = "ok", data = Unit)
                }

                else -> error("Unexpected call: ${method.name}")
            }
        } as LongCareApiService
        val eventBus = AppEventBus()
        val phoneSystem = "Android"
        val phoneVersion = "16"
        val networkType = "WIFI"
        val networkOperator = "Carrier"
        val request = LoginLogParamModel(
            phoneSystem = phoneSystem,
            phoneVersion = phoneVersion,
            networkType = networkType,
            networkOperator = networkOperator,
        )

        val repository = LoginRepositoryImpl(
            apiService = apiService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            eventBus = eventBus,
        )

        val result = repository.recordLoginLog(
            phoneSystem = phoneSystem,
            phoneVersion = phoneVersion,
            networkType = networkType,
            networkOperator = networkOperator,
        )

        assertTrue(result is ApiResult.Success)
        assertEquals(1, delegatedCallCount)
        assertEquals(request, delegatedParam)
    }
}
