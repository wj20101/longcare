package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import com.ytone.longcare.model.UserSessionSnapshot
import com.ytone.longcare.common.network.RequestSessionGeneration
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RequestInterceptorDeviceIdTest {

    private lateinit var consentManager: PrivacyConsentManager
    private lateinit var deviceInfoProvider: RequestDeviceInfoProvider
    private lateinit var cryptoProvider: RequestCryptoProvider
    private lateinit var userSessionRepository: UserSessionRepository
    private lateinit var runtimeConfigProvider: RuntimeConfigProvider
    private lateinit var interceptor: RequestInterceptor

    private val capturedHeaderJson = slot<ByteArray>()
    private val capturedRequestBody = slot<ByteArray>()

    @Before
    fun setUp() {
        consentManager = mockk()
        deviceInfoProvider = mockk()
        cryptoProvider = mockk()
        userSessionRepository = mockk()
        runtimeConfigProvider = mockk()

        every { runtimeConfigProvider.baseUrl } returns "https://api.example.com/"
        every { runtimeConfigProvider.isDebug } returns false
        every { runtimeConfigProvider.publicKey } returns "testPublicKey"

        every { userSessionRepository.sessionSnapshot } returns MutableStateFlow(UserSessionSnapshot(0L, null))

        every { deviceInfoProvider.getAppVersionCode() } returns 1
        every { deviceInfoProvider.getAppVersionName() } returns "1.0.0"
        every { deviceInfoProvider.getAppInstanceId() } returns "test-device-id-123"

        every { cryptoProvider.encryptRsaToHex(any(), any()) } returns "encrypted-aes-key"
        every { cryptoProvider.encryptAesToHex(capture(capturedHeaderJson), any()) } returns "encrypted-header"

        interceptor = RequestInterceptor(
            userSessionRepository = userSessionRepository,
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
    fun `account changes cannot mix identity fields within a request header`() {
        every { consentManager.isPrivacyConsented } returns false
        val first = User(userId = 1, accountId = 2, companyId = 3, userIdentity = 4, token = "first-token")
        val next = User(userId = 11, accountId = 12, companyId = 13, userIdentity = 14, token = "next-token")
        val snapshots = mockk<StateFlow<UserSessionSnapshot>>()
        every { userSessionRepository.sessionSnapshot } returns snapshots
        every { snapshots.value } returnsMany listOf(UserSessionSnapshot(7L, first), UserSessionSnapshot(8L, next))

        val sent = executeRequest()
        assertEquals(7L, sent.tag(RequestSessionGeneration::class.java)?.value)

        val header = JSONObject(String(capturedHeaderJson.captured, Charsets.UTF_8))
        assertEquals(first.userId, header.getInt("userId"))
        assertEquals(first.accountId, header.getInt("accountId"))
        assertEquals(first.companyId, header.getInt("companyId"))
        assertEquals(first.userIdentity, header.getInt("userIdentity"))
        assertEquals(first.token, header.getString("token"))
    }

    @Test
    fun `unknown or changing snapshot does not authenticate or attach a generation`() {
        every { consentManager.isPrivacyConsented } returns false
        every { userSessionRepository.sessionState } returns MutableStateFlow(SessionState.LoggedIn(User(userId = 99, token = "old")))
        every { userSessionRepository.sessionSnapshot } returns MutableStateFlow(UserSessionSnapshot(8L, null))
        val sent = executeRequest()
        val header = JSONObject(String(capturedHeaderJson.captured, Charsets.UTF_8))
        assertEquals(0, header.getInt("userId"))
        assertEquals("", header.getString("token"))
        assertEquals(null, sent.tag(RequestSessionGeneration::class.java))
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

    private fun executeRequest(
        request: Request =
            Request.Builder()
                .url("https://api.example.com/V1/Test/Endpoint")
                .build()
    ): Request {

        val dummyResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()

        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        val sent = slot<Request>()
        every { chain.proceed(capture(sent)) } returns dummyResponse

        interceptor.intercept(chain)
        return sent.captured
    }
}
