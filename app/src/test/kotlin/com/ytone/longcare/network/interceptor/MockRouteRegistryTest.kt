package com.ytone.longcare.network.interceptor

import com.google.common.truth.Truth.assertThat
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Test

class MockRouteRegistryTest {
    private val registry = MockRouteRegistry.create(MockAssetLoader { "{}" })

    @Test
    fun `registered route keys are unique`() {
        val keys = registry.routes.map(MockRoute::key)

        assertThat(keys.toSet()).hasSize(keys.size)
    }

    @Test
    fun `query does not alter exact method and encoded path match`() {
        val request = get("/V1/System/Start?source=smoke")

        assertThat(registry.find(request)?.key)
            .isEqualTo(MockRouteKey.of("GET", "/V1/System/Start"))
    }

    @Test
    fun `path matching remains case sensitive`() {
        assertThat(registry.find(get("/v1/System/Start"))).isNull()
    }

    @Test
    fun `same path with different method does not match`() {
        assertThat(registry.find(post("/V1/System/Start"))).isNull()
    }

    @Test
    fun `duplicate method and path are rejected when registry is created`() {
        val route = MockRoute(
            key = MockRouteKey.of("GET", "/V1/Test"),
            contract = MockContract.UNIT,
            source = AssetMockResponse("mock/test.json"),
        )

        val failure = runCatching {
            MockRouteRegistry.createForTest(listOf(route, route))
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `all routes previously handled by the interceptor remain registered`() {
        val registeredRoutes = registry.routes.associateBy(MockRoute::key)

        assertThat(registeredRoutes.keys).containsAtLeastElementsIn(LEGACY_ROUTE_ASSETS.keys)
        LEGACY_ROUTE_ASSETS.forEach { (key, expectedAsset) ->
            val source = registeredRoutes.getValue(key).source
            assertThat(source).isInstanceOf(AssetMockResponse::class.java)
            assertThat((source as AssetMockResponse).assetFor(MockScenario.DEFAULT))
                .isEqualTo(expectedAsset)
        }
        assertThat(registeredRoutes.getValue(MockRouteKey.of("GET", "/V1/Common/Config")).source)
            .isInstanceOf(GeneratedMockResponse::class.java)
        assertThat(registeredRoutes.getValue(MockRouteKey.of("GET", "/V1/System/Config")).source)
            .isInstanceOf(GeneratedMockResponse::class.java)
    }

    private fun get(path: String): Request = Request.Builder()
        .url("https://careapi.ytone.cn$path")
        .get()
        .build()

    private fun post(path: String): Request = Request.Builder()
        .url("https://careapi.ytone.cn$path")
        .post("{}".toRequestBody())
        .build()

    private companion object {
        const val COMMON_SUCCESS = "mock/common_success_unit.json"
        val LEGACY_ROUTE_ASSETS = mapOf(
            MockRouteKey.of("GET", "/V1/System/Start") to "mock/start_config.json",
            MockRouteKey.of("POST", "/V1/Phone/SendSmsCode") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Login/Phone") to "mock/login_phone.json",
            MockRouteKey.of("POST", "/V1/Login/Log") to COMMON_SUCCESS,
            MockRouteKey.of("GET", "/V1/Login/Out") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/OrderList") to "mock/order_list.json",
            MockRouteKey.of("GET", "/V1/Service/TodayOrder") to "mock/today_order_list.json",
            MockRouteKey.of("GET", "/V1/Service/InOrder") to "mock/in_order_list.json",
            MockRouteKey.of("POST", "/V1/Service/OrderInfo") to "mock/order_info.json",
            MockRouteKey.of("POST", "/V1/Service/StarOrder") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/EndOrder") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/AddPostion") to COMMON_SUCCESS,
            MockRouteKey.of("GET", "/V1/Service/Statistics") to "mock/service_statistics.json",
            MockRouteKey.of("GET", "/V1/Service/HaveServiceUserList") to "mock/user_info_list.json",
            MockRouteKey.of("GET", "/V1/Service/NoServiceUserList") to "mock/user_info_list.json",
            MockRouteKey.of("POST", "/V1/Service/UserOrderList") to "mock/user_order_list.json",
            MockRouteKey.of("POST", "/V1/Service/CheckOrder") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/UpUserStartImg") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/CheckEndOrder") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/Service/OrderState") to "mock/order_state_in_progress.json",
            MockRouteKey.of("POST", "/V1/File/UploadToken") to "mock/upload_token.json",
            MockRouteKey.of("POST", "/V1/File/GetFileUrl") to "mock/file_url.json",
            MockRouteKey.of("GET", "/V1/System/ChecVersion") to "mock/app_version.json",
            MockRouteKey.of("POST", "/V1/User/SetFace") to COMMON_SUCCESS,
            MockRouteKey.of("POST", "/V1/User/CheckFace") to COMMON_SUCCESS,
        )
    }
}
