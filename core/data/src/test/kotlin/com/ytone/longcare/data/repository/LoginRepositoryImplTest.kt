package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.Response
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        repository.recordLoginLog(
            phoneSystem = phoneSystem,
            phoneVersion = phoneVersion,
            networkType = networkType,
            networkOperator = networkOperator,
        )

        assertEquals(1, delegatedCallCount)
        assertEquals(request, delegatedParam)
    }

    @Test
    fun `recordLoginLog does not emit force logout on 3002`() = runTest(StandardTestDispatcher()) {
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "recordLoginLog" -> Response(resultCode = 3002, resultMsg = "login expired", data = null)
                else -> error("Unexpected call: ${method.name}")
            }
        } as LongCareApiService
        val eventBus = AppEventBus()
        var receivedForceLogout = false
        val collector = launch {
            receivedForceLogout = eventBus.events.first() is AppEvent.ForceLogout
        }

        val repository = LoginRepositoryImpl(
            apiService = apiService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            eventBus = eventBus,
        )

        repository.recordLoginLog(
            phoneSystem = "Android",
            phoneVersion = "16",
            networkType = "WIFI",
            networkOperator = "Carrier",
        )

        collector.cancel()

        assertFalse(receivedForceLogout)
    }
}
