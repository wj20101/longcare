package com.ytone.longcare.network.interceptor

import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.common.utils.ThirdKeyDecryptUtils
import com.ytone.longcare.model.Response as ApiResponse
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel
import java.io.IOException
import java.util.Locale
import okhttp3.Request

internal data class MockRouteKey(
    val method: String,
    val encodedPath: String,
) {
    companion object {
        fun from(request: Request): MockRouteKey =
            of(method = request.method, encodedPath = request.url.encodedPath)

        fun of(method: String, encodedPath: String): MockRouteKey =
            MockRouteKey(
                method = method.uppercase(Locale.ROOT),
                encodedPath = encodedPath.withLeadingSlash(),
            )

        private fun String.withLeadingSlash(): String =
            if (startsWith('/')) this else "/$this"
    }
}

internal enum class MockScenario {
    DEFAULT,
    CHECK_END_ORDER_REQUIRES_CONFIRMATION,
    ORDER_STATE_COMPLETED,
    ORDER_STATE_CANCELLED,
    ORDER_STATE_PENDING,
    ORDER_STATE_NOT_CREATED,
    FACE_REGISTERED,
    UPDATE_AVAILABLE,
}

internal fun interface MockScenarioProvider {
    fun scenarioFor(routeKey: MockRouteKey): MockScenario
}

internal object DefaultMockScenarioProvider : MockScenarioProvider {
    override fun scenarioFor(routeKey: MockRouteKey): MockScenario = MockScenario.DEFAULT
}

internal fun interface MockAssetLoader {
    @Throws(IOException::class)
    fun read(assetPath: String): String
}

internal enum class MockContract {
    UNIT,
    LOGIN,
    ORDER_LIST,
    TODAY_ORDER_LIST,
    ORDER_INFO,
    END_ORDER,
    SERVICE_STATISTICS,
    USER_INFO_LIST,
    USER_ORDER_LIST,
    UPLOAD_TOKEN,
    FILE_URL,
    SYSTEM_CONFIG,
    START_CONFIG,
    APP_VERSION,
    FACE,
    ORDER_STATE,
}

internal sealed interface MockResponseSource {
    @Throws(IOException::class)
    fun body(
        request: Request,
        scenario: MockScenario,
        assetLoader: MockAssetLoader,
    ): String

    fun sourceLabel(scenario: MockScenario): String
}

internal data class AssetMockResponse(
    private val defaultAsset: String,
    private val scenarioAssets: Map<MockScenario, String> = emptyMap(),
) : MockResponseSource {
    override fun body(
        request: Request,
        scenario: MockScenario,
        assetLoader: MockAssetLoader,
    ): String = assetLoader.read(assetFor(scenario))

    fun assetFor(scenario: MockScenario): String = scenarioAssets[scenario] ?: defaultAsset

    fun supportedScenarios(): Set<MockScenario> = setOf(MockScenario.DEFAULT) + scenarioAssets.keys

    override fun sourceLabel(scenario: MockScenario): String = assetFor(scenario)
}

internal fun interface GeneratedMockBody {
    @Throws(IOException::class)
    fun create(request: Request): String
}

internal data class GeneratedMockResponse(
    private val bodyFactory: GeneratedMockBody,
) : MockResponseSource {
    override fun body(
        request: Request,
        scenario: MockScenario,
        assetLoader: MockAssetLoader,
    ): String = bodyFactory.create(request)

    override fun sourceLabel(scenario: MockScenario): String = "generated"
}

internal data class MockRoute(
    val key: MockRouteKey,
    val contract: MockContract,
    val source: MockResponseSource,
    val allowedResultCodes: Set<Int> = setOf(SUCCESS_RESULT_CODE),
)

internal class MissingMockRouteException(
    routeKey: MockRouteKey,
) : IOException("Missing debug mock route: ${routeKey.method} ${routeKey.encodedPath}")

internal class InvalidMockFixtureException(
    routeKey: MockRouteKey,
    cause: Throwable? = null,
) : IOException("Invalid debug mock fixture: ${routeKey.method} ${routeKey.encodedPath}", cause)

internal class MockRouteRegistry private constructor(
    routes: List<MockRoute>,
    private val assetLoader: MockAssetLoader,
) {
    private val routesByKey: Map<MockRouteKey, MockRoute>

    init {
        val duplicateKeys = routes.groupingBy(MockRoute::key).eachCount().filterValues { it > 1 }.keys
        require(duplicateKeys.isEmpty()) {
            "Duplicate debug mock routes: ${duplicateKeys.joinToString()}"
        }
        routesByKey = routes.associateBy(MockRoute::key)
    }

    val routes: Collection<MockRoute>
        get() = routesByKey.values

    fun find(request: Request): MockRoute? = routesByKey[MockRouteKey.from(request)]

    @Throws(IOException::class)
    fun responseBody(
        route: MockRoute,
        request: Request,
        scenarioProvider: MockScenarioProvider,
    ): String = try {
        route.source.body(
            request = request,
            scenario = scenarioProvider.scenarioFor(route.key),
            assetLoader = assetLoader,
        )
    } catch (error: InvalidMockFixtureException) {
        throw error
    } catch (error: Exception) {
        throw InvalidMockFixtureException(route.key, error)
    }

    companion object {
        fun create(assetLoader: MockAssetLoader): MockRouteRegistry =
            MockRouteRegistry(routes = defaultRoutes(), assetLoader = assetLoader)

        internal fun createForTest(
            routes: List<MockRoute>,
            assetLoader: MockAssetLoader = MockAssetLoader { "{}" },
        ): MockRouteRegistry = MockRouteRegistry(routes = routes, assetLoader = assetLoader)

        private fun defaultRoutes(): List<MockRoute> = listOf(
            asset("GET", "/V1/System/Start", "mock/start_config.json", MockContract.START_CONFIG),
            asset("POST", "/V1/Phone/SendSmsCode", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/Login/Phone", "mock/login_phone.json", MockContract.LOGIN),
            asset("POST", "/V1/Login/Log", COMMON_SUCCESS, MockContract.UNIT),
            asset("GET", "/V1/Login/Out", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/Service/OrderList", "mock/order_list.json", MockContract.ORDER_LIST),
            asset("GET", "/V1/Service/TodayOrder", "mock/today_order_list.json", MockContract.TODAY_ORDER_LIST),
            asset("GET", "/V1/Service/InOrder", "mock/in_order_list.json", MockContract.ORDER_LIST),
            asset("POST", "/V1/Service/OrderInfo", "mock/order_info.json", MockContract.ORDER_INFO),
            asset("POST", "/V1/Service/StarOrder", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/Service/EndOrder", COMMON_SUCCESS, MockContract.END_ORDER),
            asset("POST", "/V1/Service/AddPostion", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/Service/BindLocation", COMMON_SUCCESS, MockContract.UNIT),
            asset("GET", "/V1/Service/Statistics", "mock/service_statistics.json", MockContract.SERVICE_STATISTICS),
            asset("GET", "/V1/Service/HaveServiceUserList", "mock/user_info_list.json", MockContract.USER_INFO_LIST),
            asset("GET", "/V1/Service/NoServiceUserList", "mock/user_info_list.json", MockContract.USER_INFO_LIST),
            asset("POST", "/V1/Service/UserOrderList", "mock/user_order_list.json", MockContract.USER_ORDER_LIST),
            asset("POST", "/V1/Service/CheckOrder", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/Service/UpUserStartImg", COMMON_SUCCESS, MockContract.UNIT),
            asset(
                method = "POST",
                path = "/V1/Service/CheckEndOrder",
                defaultAsset = COMMON_SUCCESS,
                contract = MockContract.UNIT,
                scenarioAssets = mapOf(
                    MockScenario.CHECK_END_ORDER_REQUIRES_CONFIRMATION to
                        "mock/check_end_order_error_3005.json",
                ),
                allowedResultCodes = setOf(SUCCESS_RESULT_CODE, CHECK_END_ORDER_CONFIRMATION_CODE),
            ),
            asset(
                method = "POST",
                path = "/V1/Service/OrderState",
                defaultAsset = "mock/order_state_in_progress.json",
                contract = MockContract.ORDER_STATE,
                scenarioAssets = mapOf(
                    MockScenario.ORDER_STATE_COMPLETED to "mock/order_state_completed.json",
                    MockScenario.ORDER_STATE_CANCELLED to "mock/order_state_cancelled.json",
                    MockScenario.ORDER_STATE_PENDING to "mock/order_state_pending.json",
                    MockScenario.ORDER_STATE_NOT_CREATED to "mock/order_state_not_created.json",
                ),
            ),
            asset("POST", "/V1/File/UploadToken", "mock/upload_token.json", MockContract.UPLOAD_TOKEN),
            asset("POST", "/V1/File/GetFileUrl", "mock/file_url.json", MockContract.FILE_URL),
            generated("GET", "/V1/Common/Config", MockContract.SYSTEM_CONFIG, SYSTEM_CONFIG_RESPONSE),
            generated("GET", "/V1/System/Config", MockContract.SYSTEM_CONFIG, SYSTEM_CONFIG_RESPONSE),
            asset(
                method = "GET",
                path = "/V1/System/ChecVersion",
                defaultAsset = "mock/app_version.json",
                contract = MockContract.APP_VERSION,
                scenarioAssets = mapOf(MockScenario.UPDATE_AVAILABLE to "mock/app_version_available.json"),
            ),
            asset("POST", "/V1/User/SetFace", COMMON_SUCCESS, MockContract.UNIT),
            asset("POST", "/V1/User/CheckFace", COMMON_SUCCESS, MockContract.UNIT),
            asset(
                method = "GET",
                path = "/V1/User/GetFace",
                defaultAsset = "mock/get_face_missing.json",
                contract = MockContract.FACE,
                scenarioAssets = mapOf(MockScenario.FACE_REGISTERED to "mock/get_face_registered.json"),
            ),
        )

        private fun asset(
            method: String,
            path: String,
            defaultAsset: String,
            contract: MockContract,
            scenarioAssets: Map<MockScenario, String> = emptyMap(),
            allowedResultCodes: Set<Int> = setOf(SUCCESS_RESULT_CODE),
        ): MockRoute = MockRoute(
            key = MockRouteKey.of(method, path),
            contract = contract,
            source = AssetMockResponse(defaultAsset, scenarioAssets),
            allowedResultCodes = allowedResultCodes,
        )

        private fun generated(
            method: String,
            path: String,
            contract: MockContract,
            bodyFactory: GeneratedMockBody,
        ): MockRoute = MockRoute(
            key = MockRouteKey.of(method, path),
            contract = contract,
            source = GeneratedMockResponse(bodyFactory),
        )

        private val SYSTEM_CONFIG_RESPONSE = GeneratedMockBody { request ->
            val routeKey = MockRouteKey.from(request)
            val aesKey = request.tag(AesKeyTag::class.java)?.key
                ?.takeIf(String::isNotBlank)
                ?: throw InvalidMockFixtureException(routeKey)
            val encryptedThirdKey = ThirdKeyDecryptUtils.encryptThirdKeyModel(
                thirdKeyModel = ThirdKeyReturnModel(),
                aesKey = aesKey,
            )?.takeIf(String::isNotBlank) ?: throw InvalidMockFixtureException(routeKey)
            val responseType = Types.newParameterizedType(
                ApiResponse::class.java,
                SystemConfigModel::class.java,
            )
            val adapter = DefaultMoshi.adapter<ApiResponse<SystemConfigModel>>(responseType)
            adapter.toJson(
                ApiResponse(
                    resultCode = SUCCESS_RESULT_CODE,
                    resultMsg = "成功 (来自本地 Mock 数据)",
                    data = SystemConfigModel(
                        companyName = "LongCare Mock",
                        maxImgNum = 9,
                        syLogoImg = "",
                        selectServiceType = 0,
                        thirdKeyStr = encryptedThirdKey,
                    ),
                )
            )
        }

        private const val COMMON_SUCCESS = "mock/common_success_unit.json"
    }
}

private const val SUCCESS_RESULT_CODE = 1000
private const val CHECK_END_ORDER_CONFIRMATION_CODE = 3005

internal object MockSmokeContract {
    val routeKeys: Set<MockRouteKey> = setOf(
        MockRouteKey.of("GET", "/V1/System/Start"),
        MockRouteKey.of("POST", "/V1/Phone/SendSmsCode"),
        MockRouteKey.of("POST", "/V1/Login/Phone"),
        MockRouteKey.of("POST", "/V1/Login/Log"),
        MockRouteKey.of("GET", "/V1/Login/Out"),
        MockRouteKey.of("POST", "/V1/Service/OrderList"),
        MockRouteKey.of("GET", "/V1/Service/TodayOrder"),
        MockRouteKey.of("GET", "/V1/Service/InOrder"),
        MockRouteKey.of("POST", "/V1/Service/OrderInfo"),
        MockRouteKey.of("POST", "/V1/Service/StarOrder"),
        MockRouteKey.of("POST", "/V1/Service/EndOrder"),
        MockRouteKey.of("POST", "/V1/Service/AddPostion"),
        MockRouteKey.of("POST", "/V1/Service/BindLocation"),
        MockRouteKey.of("GET", "/V1/Service/Statistics"),
        MockRouteKey.of("GET", "/V1/Service/HaveServiceUserList"),
        MockRouteKey.of("GET", "/V1/Service/NoServiceUserList"),
        MockRouteKey.of("POST", "/V1/Service/UserOrderList"),
        MockRouteKey.of("POST", "/V1/Service/CheckOrder"),
        MockRouteKey.of("POST", "/V1/Service/UpUserStartImg"),
        MockRouteKey.of("POST", "/V1/Service/CheckEndOrder"),
        MockRouteKey.of("POST", "/V1/Service/OrderState"),
        MockRouteKey.of("POST", "/V1/File/UploadToken"),
        MockRouteKey.of("POST", "/V1/File/GetFileUrl"),
        MockRouteKey.of("GET", "/V1/System/Config"),
        MockRouteKey.of("GET", "/V1/System/ChecVersion"),
        MockRouteKey.of("POST", "/V1/User/SetFace"),
        MockRouteKey.of("GET", "/V1/User/GetFace"),
        MockRouteKey.of("POST", "/V1/User/CheckFace"),
    )
}
