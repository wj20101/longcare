package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.GetCheckTokenParamModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.ToDoNumResultModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.http.GET
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
        val toDoCount = ToDoNumResultModel(num = 2)
        val toDoList =
            listOf(
                ToDoResultModel(
                    title = "上门评估",
                    content = "请联系客户确认时间",
                    createTime = "2026-08-01 09:00:00",
                )
            )
        val detail = UserLatentDetailModel(id = 7, userName = "测试客户")
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java),
        ) { _, method, args ->
            val requestArgument = args?.dropLast(1)?.firstOrNull()
            calls += method.name to requestArgument
            when (method.name) {
                "getCheckToken" -> ApiResult.Success(token)
                "addUserLatent" -> ApiResult.Success(created)
                "getRecentUserLatentList" -> ApiResult.Success(list)
                "getToDoCount" -> ApiResult.Success(toDoCount)
                "getToDoList" -> ApiResult.Success(toDoList)
                "searchUserLatentList" -> ApiResult.Success(list)
                "getUserLatentDetail" -> ApiResult.Success(detail)
                else -> error("Unexpected call: ${method.name}")
            }
        } as LongCareApiService
        val repository = SaleRepositoryImpl(
            apiService = apiService,
        )
        val addRequest = AddUserLatentParamModel(userName = "测试客户")
        val searchRequest =
            SearchUserLatentParamModel(
                pageIndex = 3,
                userName = "测试",
            )

        assertEquals(
            ApiResult.Success(token),
            repository.getCheckToken(customerId = 7, checkDeviceId = "device-1"),
        )
        assertEquals(ApiResult.Success(created), repository.addUserLatent(addRequest))
        assertEquals(ApiResult.Success(list), repository.getRecentUserLatentList())
        assertEquals(ApiResult.Success(toDoCount), repository.getToDoCount())
        assertEquals(ApiResult.Success(toDoList), repository.getToDoList())
        assertEquals(ApiResult.Success(list), repository.searchUserLatentList(searchRequest))
        assertEquals(ApiResult.Success(detail), repository.getUserLatentDetail(7))

        assertEquals(
            listOf(
                "getCheckToken" to GetCheckTokenParamModel(7, "device-1"),
                "addUserLatent" to addRequest,
                "getRecentUserLatentList" to null,
                "getToDoCount" to null,
                "getToDoList" to null,
                "searchUserLatentList" to searchRequest,
                "getUserLatentDetail" to 7,
            ),
            calls,
        )
    }

    @Test
    fun `new to-do endpoints use documented GET paths`() {
        val methods = LongCareApiService::class.java.declaredMethods.associateBy { it.name }

        assertEquals(
            "/V1/Sale/ToDoNum",
            requireNotNull(
                methods.getValue("getToDoCount").getAnnotation(GET::class.java)
            ).value,
        )
        assertEquals(
            "/V1/Sale/ToDoList",
            requireNotNull(
                methods.getValue("getToDoList").getAnnotation(GET::class.java)
            ).value,
        )
    }
}
