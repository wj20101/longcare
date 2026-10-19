package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.result.ApiResult
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeDeviceRepositoryImplTest {

    @Test
    fun `each poll calls API once and forwards success failure and exception unchanged`() = runTest {
        val responses = listOf(
            ApiResult.Success(ZhiChiDeviceStatusModel(deviceSn = "SN-123", status = "在线")),
            ApiResult.Failure(4001, "设备暂不可用"),
            ApiResult.Exception(IOException("offline")),
        )
        var calls = 0
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java),
        ) { _, method, _ ->
            check(method.name == "getZhiChiDeviceStatus") { "Unexpected call: ${method.name}" }
            responses[calls++]
        } as LongCareApiService
        val repository = HomeDeviceRepositoryImpl(apiService)

        responses.forEach { expected ->
            assertSame(expected, repository.getDeviceStatus())
        }

        assertEquals(3, calls)
    }
}
