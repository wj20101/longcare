package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.result.ApiResult
import java.lang.reflect.Proxy
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IdentificationRepositoryImplTest {

    @Test
    fun `checkFace delegates documented request to API service`() =
        runTest(StandardTestDispatcher()) {
            val request = CheckFaceParamModel(orderId = 123, faceImg = "ZmFjZQ==")
            var delegatedRequest: CheckFaceParamModel? = null
            val expected = ApiResult.Success(Unit)
            val apiService =
                Proxy.newProxyInstance(
                    LongCareApiService::class.java.classLoader,
                    arrayOf(LongCareApiService::class.java),
                ) { _, method, args ->
                    when (method.name) {
                        "checkFace" -> {
                            delegatedRequest = args?.firstOrNull() as? CheckFaceParamModel
                            expected
                        }

                        else -> error("Unexpected call: ${method.name}")
                    }
                } as LongCareApiService
            val repository = IdentificationRepositoryImpl(apiService)

            val result = repository.checkFace(request)

            assertEquals(request, delegatedRequest)
            assertEquals(expected, result)
        }
}
