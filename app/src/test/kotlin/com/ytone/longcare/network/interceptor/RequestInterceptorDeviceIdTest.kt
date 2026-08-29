package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.data.session.RequestAuthSnapshot
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.model.UserScopeKey
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestInterceptorDeviceIdTest {

    private lateinit var consentManager: PrivacyConsentManager
    private lateinit var deviceInfoProvider: RequestDeviceInfoProvider
    private lateinit var cryptoProvider: RequestCryptoProvider
    private lateinit var sessionSecretProvider: SessionSecretProvider
    private lateinit var runtimeConfigProvider: RuntimeConfigProvider
    private lateinit var interceptor: RequestInterceptor

    private val capturedHeaderJson = slot<ByteArray>()
    private val capturedRequestBody = slot<ByteArray>()

    @Before
    fun setUp() {
        consentManager = mockk()
        deviceInfoProvider = mockk()
        cryptoProvider = mockk()
        sessionSecretProvider = mockk()
        runtimeConfigProvider = mockk()

        every { runtimeConfigProvider.baseUrl } returns "https://api.example.com/"
        every { runtimeConfigProvider.isDebug } returns false
        every { runtimeConfigProvider.publicKey } returns "testPublicKey"

        every { sessionSecretProvider.requestAuthSnapshot() } returns null

        every { deviceInfoProvider.getAppVersionCode() } returns 1
        every { deviceInfoProvider.getAppVersionName() } returns "1.0.0"
        every { deviceInfoProvider.getAppInstanceId() } returns "test-device-id-123"

        every { cryptoProvider.encryptRsaToHex(any(), any()) } returns "encrypted-aes-key"
        every { cryptoProvider.encryptAesToHex(capture(capturedHeaderJson), any()) } returns "encrypted-header"

        interceptor = RequestInterceptor(
            sessionSecretProvider = sessionSecretProvider,
            runtimeConfigProvider = runtimeConfigProvider,
            requestDeviceInfoProvider = deviceInfoProvider,
            requestCryptoProvider = cryptoProvider,
            privacyConsentManager = consentManager,
        )
    }

    @Test
    fun `deviceId is excluded from headers when consent is not given`() {
        every { consentManager.isPrivacyConsented } returns false

        executeRequest()

        val headerJson = String(capturedHeaderJson.captured)
        assertFalse("Header should not contain deviceId", headerJson.contains("deviceId"))
    }

    @Test
    fun `deviceId is included in headers when consent is given`() {
        every { consentManager.isPrivacyConsented } returns true

        executeRequest()

        val headerJson = String(capturedHeaderJson.captured)
        assertTrue("Header should contain deviceId", headerJson.contains("deviceId"))
        assertTrue("Header should contain actual device id value", headerJson.contains("test-device-id-123"))
    }

    @Test
    fun `getAppInstanceId is not called when consent is not given`() {
        every { consentManager.isPrivacyConsented } returns false

        executeRequest()

        verify(exactly = 0) { deviceInfoProvider.getAppInstanceId() }
    }

    @Test
    fun `request body keeps original guardian phone before encryption`() {
        every { consentManager.isPrivacyConsented } returns true
        every {
            cryptoProvider.encryptAesToHex(capture(capturedRequestBody), any())
        } returns "encrypted-payload"
        val originalBody = """{"guardianPhone":"13666665555"}"""
        val request =
            Request.Builder()
                .url("https://api.example.com/V1/Sale/AddUserLatent")
                .post(originalBody.toRequestBody("application/json".toMediaType()))
                .build()

        executeRequest(request)

        val encryptedInput = String(capturedRequestBody.captured, Charsets.UTF_8)
        assertEquals(originalBody, encryptedInput)
        assertFalse(encryptedInput.contains("136****5555"))
    }

    @Test
    fun `authentication headers come from purpose limited request snapshot`() {
        every { consentManager.isPrivacyConsented } returns false
        every { sessionSecretProvider.requestAuthSnapshot() } returns RequestAuthSnapshot(
            scopeKey = UserScopeKey(companyId = 10, accountId = 20, userId = 30),
            userIdentity = 4,
            token = "request-only-token",
            sessionEpoch = SessionEpoch(5),
        )

        executeRequest()

        val headerJson = String(capturedHeaderJson.captured)
        assertTrue(headerJson.contains("request-only-token"))
        assertTrue(headerJson.contains("\"companyId\":10"))
        assertTrue(headerJson.contains("\"accountId\":20"))
        assertTrue(headerJson.contains("\"userId\":30"))
    }

    private fun executeRequest(
        request: Request =
            Request.Builder()
                .url("https://api.example.com/V1/Test/Endpoint")
                .build()
    ) {

        val dummyResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(any()) } returns dummyResponse

        interceptor.intercept(chain)
    }
}
