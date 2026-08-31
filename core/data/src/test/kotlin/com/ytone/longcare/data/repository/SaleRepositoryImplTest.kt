package com.ytone.longcare.data.repository

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.model.AddUserLatentRequestDto
import com.ytone.longcare.api.model.AddUserLatentResponseDto
import com.ytone.longcare.api.model.CheckTokenDto
import com.ytone.longcare.api.model.GetCheckTokenRequestDto
import com.ytone.longcare.api.model.SearchUserLatentRequestDto
import com.ytone.longcare.api.model.ToDoCountDto
import com.ytone.longcare.api.model.ToDoItemDto
import com.ytone.longcare.api.model.UserLatentDetailDto
import com.ytone.longcare.api.model.UserLatentListDto
import com.ytone.longcare.common.utils.KLogger
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.AddUserLatentParamModel
import com.ytone.longcare.model.AddUserLatentResultModel
import com.ytone.longcare.model.CheckTokenModel
import com.ytone.longcare.model.SearchUserLatentParamModel
import com.ytone.longcare.model.ToDoNumResultModel
import com.ytone.longcare.model.ToDoResultModel
import com.ytone.longcare.model.UserLatentDetailModel
import com.ytone.longcare.model.UserLatentListModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query
import java.lang.reflect.Proxy

class SaleRepositoryImplTest {

    @Before
    fun disableAndroidLogging() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `all Sale operations delegate to documented API contracts`() = runTest {
        val calls = mutableListOf<Pair<String, Any?>>()
        val token = CheckTokenModel(
            token = "SDK.test",
            tokenType = 1,
            expireAt = 1234L,
            bizType = 2,
        )
        val tokenDto = CheckTokenDto("SDK.test", 1, 1234L, 2)
        val created = AddUserLatentResultModel(id = 7, pgUrl = "https://example.test/pg")
        val createdDto = AddUserLatentResponseDto(id = 7, pgUrl = "https://example.test/pg")
        val list = listOf(UserLatentListModel(id = 7, userName = "测试客户"))
        val listDto = listOf(UserLatentListDto(id = 7, userName = "测试客户"))
        val toDoCount = ToDoNumResultModel(num = 2)
        val toDoCountDto = ToDoCountDto(num = 2)
        val toDoList =
            listOf(
                ToDoResultModel(
                    title = "上门评估",
                    content = "请联系客户确认时间",
                    createTime = "2026-08-01 09:00:00",
                )
            )
        val toDoListDto =
            listOf(
                ToDoItemDto(
                    title = "上门评估",
                    content = "请联系客户确认时间",
                    createTime = "2026-08-01 09:00:00",
                )
            )
        val detail = UserLatentDetailModel(id = 7, userName = "测试客户")
        val detailDto = UserLatentDetailDto(id = 7, userName = "测试客户")
        val apiService = Proxy.newProxyInstance(
            LongCareApiService::class.java.classLoader,
            arrayOf(LongCareApiService::class.java),
        ) { _, method, args ->
            val requestArgument = args?.dropLast(1)?.firstOrNull()
            calls += method.name to requestArgument
            when (method.name) {
                "getCheckToken" -> ApiResult.Success(tokenDto)
                "addUserLatent" -> ApiResult.Success(createdDto)
                "getRecentUserLatentList" -> ApiResult.Success(listDto)
                "getToDoCount" -> ApiResult.Success(toDoCountDto)
                "getToDoList" -> ApiResult.Success(toDoListDto)
                "searchUserLatentList" -> ApiResult.Success(listDto)
                "getUserLatentDetail" -> ApiResult.Success(detailDto)
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
                "getCheckToken" to GetCheckTokenRequestDto(7, "device-1"),
                "addUserLatent" to AddUserLatentRequestDto(userName = "测试客户"),
                "getRecentUserLatentList" to null,
                "getToDoCount" to null,
                "getToDoList" to null,
                "searchUserLatentList" to
                    SearchUserLatentRequestDto(
                        pageIndex = 3,
                        userName = "测试",
                    ),
                "getUserLatentDetail" to 7,
            ),
            calls,
        )
    }

    @Test
    fun `all Sale endpoints use documented methods and paths`() {
        val methods = LongCareApiService::class.java.declaredMethods.associateBy { it.name }

        val documentedGetPaths =
            mapOf(
                "getRecentUserLatentList" to "/V1/Sale/GetRecentUserLatentList",
                "getToDoCount" to "/V1/Sale/ToDoNum",
                "getToDoList" to "/V1/Sale/ToDoList",
                "getUserLatentDetail" to "/V1/Sale/GetUserLatentDetail",
            )
        val documentedPostPaths =
            mapOf(
                "getCheckToken" to "/V1/Sale/GetCheckToken",
                "addUserLatent" to "/V1/Sale/AddUserLatent",
                "searchUserLatentList" to "/V1/Sale/SearchUserLatentList",
            )

        assertEquals(
            documentedGetPaths,
            documentedGetPaths.keys.associateWith { methodName ->
                requireNotNull(
                    methods.getValue(methodName).getAnnotation(GET::class.java)
                ).value
            },
        )
        assertEquals(
            documentedPostPaths,
            documentedPostPaths.keys.associateWith { methodName ->
                requireNotNull(
                    methods.getValue(methodName).getAnnotation(POST::class.java)
                ).value
            },
        )

        documentedPostPaths.keys.forEach { methodName ->
            assertEquals(
                true,
                methods.getValue(methodName).parameterAnnotations.any { annotations ->
                    annotations.any { it is Body }
                },
            )
        }
        assertEquals(
            "id",
            methods
                .getValue("getUserLatentDetail")
                .parameterAnnotations
                .flatMap { it.asIterable() }
                .filterIsInstance<Query>()
                .single()
                .value,
        )
    }

    @Test
    fun `Sale DTO JSON keys exactly match the documented contract`() {
        assertEquals(
            setOf("id", "checkDeviceId"),
            jsonKeys(GetCheckTokenRequestDto(id = 7, checkDeviceId = "device-1")),
        )
        assertEquals(
            setOf(
                "userName",
                "identityCardNumber",
                "guardianName",
                "guardianPhone",
                "guardianRelation",
                "liveAddress",
                "liveLng",
                "liveLat",
                "img1",
                "img2",
                "img3",
            ),
            jsonKeys(
                AddUserLatentRequestDto(
                    userName = "客户",
                    identityCardNumber = "330000000000000000",
                    guardianName = "监护人",
                    guardianPhone = "13600000000",
                    guardianRelation = "子女",
                    liveAddress = "地址",
                    liveLng = "120.0",
                    liveLat = "30.0",
                    img1 = "key-1",
                    img2 = "key-2",
                    img3 = "key-3",
                )
            ),
        )
        assertEquals(
            setOf("pageIndex", "userName", "checkState"),
            jsonKeys(SearchUserLatentRequestDto(pageIndex = 2, userName = "客户", checkState = 1)),
        )
        assertEquals(
            setOf("token", "tokenType", "expireAt", "bizType"),
            jsonKeys(CheckTokenDto("token", 1, 2L, 3)),
        )
        assertEquals(
            setOf("id", "pgUrl"),
            jsonKeys(AddUserLatentResponseDto(id = 7, pgUrl = "url")),
        )
        assertEquals(
            setOf("id", "userName", "checkState", "liveAddress", "identityCardNumber"),
            jsonKeys(UserLatentListDto(7, "客户", 1, "地址", "证件号")),
        )
        assertEquals(setOf("num"), jsonKeys(ToDoCountDto(num = 2)))
        assertEquals(
            setOf("title", "content", "createTime"),
            jsonKeys(ToDoItemDto("标题", "内容", "时间")),
        )
        assertEquals(
            setOf(
                "id",
                "userName",
                "identityCardNumber",
                "guardianName",
                "guardianPhone",
                "guardianRelation",
                "liveAddress",
                "liveLng",
                "liveLat",
                "img1",
                "img2",
                "img3",
                "checkStatus",
                "checkTime",
                "checkDesc",
                "createTime",
                "pgId",
                "pgResult",
                "pgScore",
                "pgUrl",
            ),
            jsonKeys(
                UserLatentDetailDto(
                    id = 7,
                    userName = "客户",
                    identityCardNumber = "证件号",
                    guardianName = "监护人",
                    guardianPhone = "手机号",
                    guardianRelation = "关系",
                    liveAddress = "地址",
                    liveLng = "120.0",
                    liveLat = "30.0",
                    img1 = "key-1",
                    img2 = "key-2",
                    img3 = "key-3",
                    checkStatus = 1,
                    checkTime = "审核时间",
                    checkDesc = "审核说明",
                    createTime = "创建时间",
                    pgId = 8,
                    pgResult = "评估结果",
                    pgScore = 90,
                    pgUrl = "报告地址",
                )
            ),
        )
    }

    private inline fun <reified T : Any> jsonKeys(value: T): Set<String> {
        val moshi = Moshi.Builder().build()
        val json = moshi.adapter(T::class.java).serializeNulls().toJson(value)
        val mapType =
            Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val jsonObject = requireNotNull(moshi.adapter<Map<String, Any?>>(mapType).fromJson(json))
        return jsonObject.keys
    }
}
