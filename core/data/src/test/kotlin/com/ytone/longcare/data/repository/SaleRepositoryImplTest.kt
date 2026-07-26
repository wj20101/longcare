package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.GetCheckTokenParamModel
import com.ytone.longcare.model.Response
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy

class SaleRepositoryImplTest {

    @Test
    fun `all Sale operations delegate to documented API contracts`() = runTest {
        val calls = mutableListOf<Pair<String, Any?>>()
        val token = CheckTokenModel(
            token = "SDK.test",
            tokenType = 1,
            expireAt = 1234L,
            bizType = 2,
        )
        val created = AddUserLatentResultModel(id = 7, pgUrl = "https://example.test/pg")
        val list = listOf(UserLatentListModel(id = 7, userName = "测试客户"))
        val detail = UserLatentDetailModel(id = 7, userName = "测试客户")
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java),
        ) { _, method, args ->
            val requestArgument =
                if (method.name == "getRecentUserLatentList") {
                    null
                } else {
                    args?.firstOrNull()
                }
            calls += method.name to requestArgument
            when (method.name) {
                "getCheckToken" -> Response(1000, "ok", token)
                "addUserLatent" -> Response(1000, "ok", created)
                "getRecentUserLatentList" -> Response(1000, "ok", list)
                "searchUserLatentList" -> Response(1000, "ok", list)
                "getUserLatentDetail" -> Response(1000, "ok", detail)
                else -> error("Unexpected call: ${method.name}")
            }
        } as LongCareApiService
        val repository = SaleRepositoryImpl(
            apiService = apiService,
            ioDispatcher = StandardTestDispatcher(testScheduler),
            eventBus = AppEventBus(),
        )
        val addRequest = AddUserLatentParamModel(userName = "测试客户")
        val searchRequest = SearchUserLatentParamModel(userName = "测试")

        assertEquals(
            ApiResult.Success(token),
            repository.getCheckToken(customerId = 7, checkDeviceId = "device-1"),
        )
        assertEquals(ApiResult.Success(created), repository.addUserLatent(addRequest))
        assertEquals(ApiResult.Success(list), repository.getRecentUserLatentList())
        assertEquals(ApiResult.Success(list), repository.searchUserLatentList(searchRequest))
        assertEquals(ApiResult.Success(detail), repository.getUserLatentDetail(7))

        assertEquals(
            listOf(
                "getCheckToken" to GetCheckTokenParamModel(7, "device-1"),
                "addUserLatent" to addRequest,
                "getRecentUserLatentList" to null,
                "searchUserLatentList" to searchRequest,
                "getUserLatentDetail" to 7,
            ),
            calls,
        )
    }
}
