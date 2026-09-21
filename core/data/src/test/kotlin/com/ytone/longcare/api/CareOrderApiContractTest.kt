package com.ytone.longcare.api

import com.squareup.moshi.Moshi
import com.ytone.longcare.common.network.ApiRequestException
import com.ytone.longcare.common.network.ApiResultCallAdapterFactory
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.data.repository.OrderMapper.toOrderElderInfoEntity
import com.ytone.longcare.data.repository.OrderMapper.toOrderProjectEntity
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class CareOrderApiContractTest {
    @Test fun `today order accepts each nullable field and missing fields`() = runTest {
        for (field in listOf("name", "callPhone", "identityCardNumber", "liveAddress")) {
            val result = api("""[{"orderId":42,"$field":null}]""").getTodayOrderList()
            assertTrue("nullable $field: $result", result is ApiResult.Success)
        }
        val order = (api("""[{"orderId":42}]""").getTodayOrderList() as ApiResult.Success).data.single()
        assertNull(order.name)
        assertNull(order.callPhone)
        assertNull(order.identityCardNumber)
        assertNull(order.liveAddress)
    }

    @Test fun `detail accepts each nullable field and missing fields`() = runTest {
        for (field in listOf("name", "identityCardNumber", "gender", "address", "lng", "lat")) {
            val result = api("""{"orderId":42,"userInfo":{"$field":null}}""")
                .getOrderInfo(OrderInfoParamModel(42))
            assertTrue("nullable $field: $result", result is ApiResult.Success)
        }
        for (project in listOf("{}", """{"projectName":null}""")) {
            val result = api("""{"orderId":42,"userInfo":{},"projectList":[$project]}""")
                .getOrderInfo(OrderInfoParamModel(42)) as ApiResult.Success
            val user = requireNotNull(result.data.userInfo)
            assertNull(user.name)
            assertNull(user.identityCardNumber)
            assertNull(user.gender)
            assertNull(user.address)
            assertNull(user.lng)
            assertNull(user.lat)
            val entity = user.toOrderElderInfoEntity(42)
            assertEquals("", entity.elderName)
            assertEquals("", entity.elderIdCard)
            assertEquals("", entity.elderLng)
            assertEquals("", entity.elderLat)
            val item = result.data.projectList!!.single()
            assertNull(item.projectName)
            assertEquals("", item.toOrderProjectEntity(42).projectName)
        }
    }

    @Test fun `valid values and request contract are preserved`() = runTest {
        val service = api("""{"orderId":42,"userInfo":{"name":"测试对象","identityCardNumber":"test-id","gender":"女","address":"测试地址","lng":"121.5","lat":"31.2"},"projectList":[{"projectId":7,"projectName":"测试项目"}]}""") { request ->
            assertEquals("POST", request.method)
            assertEquals("/V1/Service/OrderInfo", request.url.encodedPath)
            val buffer = okio.Buffer()
            request.body!!.writeTo(buffer)
            assertEquals("""{"orderid":42,"planid":3}""", buffer.readUtf8())
        }
        val detail = (service.getOrderInfo(OrderInfoParamModel(42, 3)) as ApiResult.Success).data
        assertEquals(42L, detail.orderId)
        assertEquals("测试对象", detail.userInfo!!.name)
        assertEquals("121.5", detail.userInfo!!.toOrderElderInfoEntity(42).elderLng)
        assertEquals("测试项目", detail.projectList!!.single().projectName)
        val today = (api("""[{"orderId":42,"name":"测试对象","callPhone":"test-phone","identityCardNumber":"test-id","liveAddress":"测试地址"}]""")
            .getTodayOrderList() as ApiResult.Success).data.single()
        assertEquals("测试对象", today.name)
        assertEquals("test-phone", today.callPhone)
        assertEquals("test-id", today.identityCardNumber)
        assertEquals("测试地址", today.liveAddress)
    }

    @Test fun `invalid types malformed json and empty data remain errors`() = runTest {
        for (data in listOf("null", "{", """{"userInfo":{"name":{}}}""", """{"orderId":"invalid"}""")) {
            val result = api(data).getOrderInfo(OrderInfoParamModel(42))
            assertTrue(result is ApiResult.Exception)
            assertEquals(ApiRequestException.Kind.INVALID_RESPONSE,
                ((result as ApiResult.Exception).exception as ApiRequestException).kind)
        }
        assertTrue(api("""[{"name":{}}]""").getTodayOrderList() is ApiResult.Exception)
    }

    @Test fun `business failure remains a business failure`() = runTest {
        val result = api("null", 4001, "当前计划不可执行").getOrderInfo(OrderInfoParamModel(42))
        assertEquals(ApiResult.Failure(4001, "当前计划不可执行"), result)
    }

    private fun api(
        data: String,
        code: Int = 1000,
        message: String = "ok",
        inspect: (okhttp3.Request) -> Unit = {},
    ): LongCareApiService = Retrofit.Builder()
        .baseUrl("https://example.test/")
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            inspect(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK")
                .body("""{"resultCode":$code,"resultMsg":"$message","data":$data}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build())
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .addCallAdapterFactory(ApiResultCallAdapterFactory(mockk<SessionInvalidationHandler>(relaxed = true)))
        .build().create(LongCareApiService::class.java)
}
