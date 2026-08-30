package com.ytone.longcare.network.interceptor

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.DefaultMoshi
import com.ytone.longcare.common.utils.ThirdKeyDecryptUtils
import com.ytone.longcare.domain.facecache.FaceCacheCleaner
import com.ytone.longcare.domain.identification.IdentificationRepository
import com.ytone.longcare.features.identification.data.VerifyServicePersonDataGatewayImpl
import com.ytone.longcare.features.identification.domain.ServicePersonFaceSource
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.EndOrderResultModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.Response as ApiResponse
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.StartConfigResultModel
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.UploadTokenResultModel
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.model.UserOrderModel
import com.ytone.longcare.model.result.ApiResult
import io.mockk.coEvery
import io.mockk.mockk
import java.lang.reflect.Type
import java.net.URI
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MockFixtureContractTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val registry = MockRouteRegistry.create(
        MockAssetLoader { assetPath ->
            context.assets.open(assetPath).bufferedReader().use { it.readText() }
        }
    )

    @Test
    fun `all registered route scenarios are deterministic and satisfy their API contract`() {
        registry.routes.forEach { route ->
            route.scenarios().forEach { scenario ->
                val request = request(route.key)
                val provider = MockScenarioProvider { scenario }

                val first = registry.responseBody(route, request, provider)
                val second = registry.responseBody(route, request, provider)

                assertThat(second).isEqualTo(first)
                validate(route, scenario, first)
            }
        }
    }

    @Test
    fun `safe smoke inventory has a registered response contract for every route`() {
        val registered = registry.routes.associateBy(MockRoute::key)

        assertThat(registered.keys).containsAtLeastElementsIn(MockSmokeContract.routeKeys)
        MockSmokeContract.routeKeys.forEach { key ->
            assertThat(registered.getValue(key).contract).isNotNull()
        }
    }

    @Test
    fun `invalid encrypted system config fails with route and source diagnostics`() {
        val route = registry.routes.single {
            it.key == MockRouteKey.of("GET", "/V1/System/Config")
        }
        val invalidJson =
            """{"resultCode":1000,"resultMsg":"Success","data":{"thirdKeyStr":"not-hex"}}"""

        val failure = runCatching {
            validate(route, MockScenario.DEFAULT, invalidJson)
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(failure?.message).contains("GET /V1/System/Config")
        assertThat(failure?.message).contains("generated")
    }

    @Test
    fun `face scenarios expose explicit setup and registered states`() {
        val route = registry.routes.single {
            it.key == MockRouteKey.of("GET", "/V1/User/GetFace")
        }

        val missing = parse(
            route,
            registry.responseBody(route, request(route.key), MockScenarioProvider { MockScenario.DEFAULT }),
        ).data as FaceResultModel
        val registered = parse(
            route,
            registry.responseBody(
                route,
                request(route.key),
                MockScenarioProvider { MockScenario.FACE_REGISTERED },
            ),
        ).data as FaceResultModel

        assertThat(missing.faceImgUrl).isEmpty()
        assertThat(registered.faceImgUrl).isEqualTo("mock/user/registered-face.jpg")
    }

    @Test
    fun `face fixture scenarios drive the existing setup and verification decisions`() = runTest {
        val route = registry.routes.single {
            it.key == MockRouteKey.of("GET", "/V1/User/GetFace")
        }
        val missing = parse(
            route,
            registry.responseBody(route, request(route.key), MockScenarioProvider { MockScenario.DEFAULT }),
        ).data as FaceResultModel
        val registered = parse(
            route,
            registry.responseBody(
                route,
                request(route.key),
                MockScenarioProvider { MockScenario.FACE_REGISTERED },
            ),
        ).data as FaceResultModel
        val repository = mockk<IdentificationRepository>()
        val gateway = VerifyServicePersonDataGatewayImpl(
            faceCacheCleaner = mockk<FaceCacheCleaner>(relaxed = true),
            identificationRepository = repository,
        )

        coEvery { repository.getFace() } returns ApiResult.Success(missing)
        assertThat(gateway.resolveFaceSource()).isEqualTo(ServicePersonFaceSource.RequireFaceSetup)

        coEvery { repository.getFace() } returns ApiResult.Success(registered)
        assertThat(gateway.resolveFaceSource())
            .isEqualTo(ServicePersonFaceSource.RegisteredFaceAvailable)
    }

    @Test
    fun `version scenarios separate no-update and safe update prompt data`() {
        val route = registry.routes.single {
            it.key == MockRouteKey.of("GET", "/V1/System/ChecVersion")
        }

        val current = parse(
            route,
            registry.responseBody(route, request(route.key), MockScenarioProvider { MockScenario.DEFAULT }),
        ).data as AppVersionModel
        val available = parse(
            route,
            registry.responseBody(
                route,
                request(route.key),
                MockScenarioProvider { MockScenario.UPDATE_AVAILABLE },
            ),
        ).data as AppVersionModel

        assertThat(current.versionCode).isEqualTo(1)
        assertThat(current.downUrl).isEmpty()
        assertThat(available.versionCode).isEqualTo(999)
        assertThat(URI(available.downUrl).host).endsWith(".mock.invalid")
    }

    private fun MockRoute.scenarios(): Set<MockScenario> =
        (source as? AssetMockResponse)?.supportedScenarios() ?: setOf(MockScenario.DEFAULT)

    private fun request(key: MockRouteKey): Request {
        val builder = Request.Builder()
            .url("https://careapi.ytone.cn${key.encodedPath}?ignored=query-secret")
            .tag(AesKeyTag::class.java, AesKeyTag(FIXED_AES_KEY))
        return if (key.method == "GET") {
            builder.get().build()
        } else {
            builder.method(key.method, "{}".toRequestBody()).build()
        }
    }

    private fun validate(
        route: MockRoute,
        scenario: MockScenario,
        json: String,
    ) {
        try {
            val response = parse(route, json)
            require(response.resultCode in route.allowedResultCodes) {
                "unexpected resultCode=${response.resultCode}"
            }
            if (response.resultCode == SUCCESS_RESULT_CODE) {
                validateSuccessData(route.contract, requireNotNull(response.data))
            }
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Invalid Mock contract for ${route.key.method} ${route.key.encodedPath} " +
                    "(${route.source.sourceLabel(scenario)})",
                error,
            )
        }
    }

    private fun parse(route: MockRoute, json: String): ApiResponse<*> {
        val responseType = Types.newParameterizedType(
            ApiResponse::class.java,
            route.contract.dataType(),
        )
        return requireNotNull(
            DefaultMoshi.adapter<ApiResponse<Any>>(responseType).fromJson(json)
        )
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private fun validateSuccessData(contract: MockContract, data: Any) {
        when (contract) {
            MockContract.UNIT -> assertThat(data).isEqualTo(Unit)
            MockContract.LOGIN -> {
                data as LoginResultModel
                require(data.companyId > 0 && data.accountId > 0 && data.userId > 0)
                require(data.token.startsWith("mock-"))
            }
            MockContract.ORDER_LIST -> {
                val orders = data as List<*>
                require(orders.all { it is ServiceOrderModel && it.orderId > 0 })
            }
            MockContract.TODAY_ORDER_LIST -> {
                val orders = data as List<*>
                require(orders.all { it is TodayServiceOrderModel && it.orderId > 0 })
            }
            MockContract.ORDER_INFO -> {
                data as ServiceOrderInfoModel
                require(data.orderId > 0 && data.userInfo != null)
            }
            MockContract.END_ORDER -> {
                data as EndOrderResultModel
                require(data.trueServiceTime >= 0)
            }
            MockContract.SERVICE_STATISTICS -> {
                data as NurseServiceTimeModel
                require(data.haveServiceTime >= 0 && data.haveServiceNum >= 0 && data.noServiceTime >= 0)
            }
            MockContract.USER_INFO_LIST -> {
                val users = data as List<*>
                require(users.all { it is UserInfoModel && it.userId > 0 })
            }
            MockContract.USER_ORDER_LIST -> {
                val orders = data as List<*>
                require(orders.all { it is UserOrderModel && it.ordreId > 0 })
            }
            MockContract.UPLOAD_TOKEN -> validateUploadToken(data as UploadTokenResultModel)
            MockContract.FILE_URL -> {
                val uri = URI(data as String)
                require(uri.scheme == "https" && uri.host.endsWith(".mock.invalid"))
                require(uri.rawQuery.isNullOrEmpty())
            }
            MockContract.SYSTEM_CONFIG -> validateSystemConfig(data as SystemConfigModel)
            MockContract.START_CONFIG -> {
                data as StartConfigResultModel
                require(URI(data.userXieYiUrl).host.endsWith(".mock.invalid"))
                require(URI(data.yinSiXieYiUrl).host.endsWith(".mock.invalid"))
            }
            MockContract.APP_VERSION -> {
                data as AppVersionModel
                require(data.versionCode > 0 && data.platform.equals("android", ignoreCase = true))
                if (data.downUrl.isNotBlank()) {
                    require(URI(data.downUrl).host.endsWith(".mock.invalid"))
                }
            }
            MockContract.FACE -> data as FaceResultModel
            MockContract.ORDER_STATE -> {
                data as ServiceOrderStateModel
                require(data.state in VALID_ORDER_STATES)
            }
        }
    }

    private fun validateUploadToken(token: UploadTokenResultModel) {
        require(token.tmpSecretId.startsWith("mock-not-valid-"))
        require(token.tmpSecretKey.startsWith("mock-not-valid-"))
        require(token.sessionToken.startsWith("mock-not-valid-"))
        require(token.bucket.startsWith("mock-not-valid-"))
        require(token.region == "mock-region")
        require(token.fileKeyPre == "mock/")
        val startTime = requireNotNull(token.startTime.toLongOrNull())
        val expiredTime = requireNotNull(token.expiredTime.toLongOrNull())
        require(startTime < expiredTime && expiredTime >= SAFE_EXPIRY_EPOCH_SECONDS)
    }

    private fun validateSystemConfig(config: SystemConfigModel) {
        val encrypted = config.thirdKeyStr
        require(encrypted.length % 2 == 0 && encrypted.matches(HEX_PATTERN))
        val decrypted = requireNotNull(
            ThirdKeyDecryptUtils.decryptThirdKeyStr(encrypted, FIXED_AES_KEY)
        )
        require(decrypted.gaoDeMapApiKey.isBlank())
        require(decrypted.txFaceAppId.isBlank())
        require(decrypted.txFaceAppSecret.isBlank())
        require(decrypted.txFaceAppLicence.isBlank())
    }

    private fun MockContract.dataType(): Type = when (this) {
        MockContract.UNIT -> Unit::class.java
        MockContract.LOGIN -> LoginResultModel::class.java
        MockContract.ORDER_LIST -> listOf(ServiceOrderModel::class.java)
        MockContract.TODAY_ORDER_LIST -> listOf(TodayServiceOrderModel::class.java)
        MockContract.ORDER_INFO -> ServiceOrderInfoModel::class.java
        MockContract.END_ORDER -> EndOrderResultModel::class.java
        MockContract.SERVICE_STATISTICS -> NurseServiceTimeModel::class.java
        MockContract.USER_INFO_LIST -> listOf(UserInfoModel::class.java)
        MockContract.USER_ORDER_LIST -> listOf(UserOrderModel::class.java)
        MockContract.UPLOAD_TOKEN -> UploadTokenResultModel::class.java
        MockContract.FILE_URL -> String::class.java
        MockContract.SYSTEM_CONFIG -> SystemConfigModel::class.java
        MockContract.START_CONFIG -> StartConfigResultModel::class.java
        MockContract.APP_VERSION -> AppVersionModel::class.java
        MockContract.FACE -> FaceResultModel::class.java
        MockContract.ORDER_STATE -> ServiceOrderStateModel::class.java
    }

    private fun listOf(elementType: Type): Type =
        Types.newParameterizedType(List::class.java, elementType)

    private companion object {
        const val SUCCESS_RESULT_CODE = 1000
        const val FIXED_AES_KEY = "0123456789ABCDEF0123456789ABCDEF"
        const val SAFE_EXPIRY_EPOCH_SECONDS = 4_000_000_000L
        val HEX_PATTERN = Regex("[0-9A-Fa-f]+")
        val VALID_ORDER_STATES = setOf(
            ServiceOrderStateModel.STATE_NOT_CREATED,
            ServiceOrderStateModel.STATE_PENDING,
            ServiceOrderStateModel.STATE_IN_PROGRESS,
            ServiceOrderStateModel.STATE_COMPLETED,
            ServiceOrderStateModel.STATE_CANCELLED,
        )
    }
}
