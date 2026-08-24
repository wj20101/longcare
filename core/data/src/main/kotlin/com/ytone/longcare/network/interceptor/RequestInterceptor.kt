package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.domain.repository.UserSessionRepository
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.security.SecureRandom
import javax.inject.Inject

class RequestInterceptor @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val runtimeConfigProvider: RuntimeConfigProvider,
    private val requestDeviceInfoProvider: RequestDeviceInfoProvider,
    private val requestCryptoProvider: RequestCryptoProvider,
    private val privacyConsentManager: PrivacyConsentManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val requestBody = request.body
        if (!url.toString().startsWith(runtimeConfigProvider.baseUrl)) {
            return chain.proceed(request)
        }

        val method = request.method
        val newRequestBuilder = request.newBuilder()
        val randomString = generateRandomString(32)
        
        // 使用OkHttp的tag机制传递AES密钥
        // 密钥只存在于当前请求的生命周期内，请求完成后自动释放
        newRequestBuilder.tag(AesKeyTag::class.java, AesKeyTag(randomString))
        
        val map = iniHttpHeader(randomString)
        if (map.isNotEmpty()) {
            for (head in map) {
                newRequestBuilder.addHeader(head.key, head.value)
            }
        }

        /*判断请求体是否为空  不为空则执行以下操作*/
        if (requestBody != null) {
            val contentType = requestBody.contentType()
            if (contentType != null) {
                if (contentType.type.lowercase() == "multipart") {
                    return chain.proceed(newRequestBuilder.build())
                }
            }

            /*获取请求的数据*/
            try {
                val buffer = Buffer()
                requestBody.writeTo(buffer)
                val requestBodyBytes = buffer.readByteArray()

                // KLogger 只对日志文本脱敏；requestBodyBytes 保持原始内容并直接用于后续加密。
                if (runtimeConfigProvider.isDebug) {
                    val requestBodyLog = if (requestBodyBytes.size <= MAX_LOGGABLE_BODY_BYTES) {
                        String(requestBodyBytes, Charsets.UTF_8)
                    } else {
                        "<omitted large request body: ${requestBodyBytes.size} bytes>"
                    }
                    logI(
                        "【请求加密前】URL: ${url}\n请求体（日志已脱敏）: $requestBodyLog",
                        tag = "RequestInterceptor",
                    )
                }

                val encryptRequest = encryptRequest(randomString, requestBodyBytes)
                val encryptData = JSONObject()
                    .put("ParamJsonString", encryptRequest)
                    .toString()
                val newRequestBody = encryptData.toRequestBody(contentType)

                //根据请求方式构建相应的请求
                when (method) {
                    "POST" -> newRequestBuilder.post(newRequestBody)
                    "PUT" -> newRequestBuilder.put(newRequestBody)
                }

            } catch (e: Exception) {
                logE(message = "加密异常====》", throwable = e)
                return chain.proceed(newRequestBuilder.build())
            }
        }
        val build = newRequestBuilder.build()
        return chain.proceed(build)
    }

    private fun iniHttpHeader(randomString: String): Map<String, String> {
        val baseMap = mutableMapOf<String, Any>(
            "userId" to (userSessionRepository.sessionState.value.user?.userId ?: 0),
            "token" to userSessionRepository.sessionState.value.user?.token.orEmpty(),
            "accountId" to (userSessionRepository.sessionState.value.user?.accountId ?: 0),
            "companyId" to (userSessionRepository.sessionState.value.user?.companyId ?: 0),
            "userIdentity" to (userSessionRepository.sessionState.value.user?.userIdentity ?: 0),
            "nonce" to randomString,
            "timeSpan" to System.currentTimeMillis(),
            "platform" to "android",
            "versionCode" to requestDeviceInfoProvider.getAppVersionCode(),
            "versionName" to requestDeviceInfoProvider.getAppVersionName(),
            "channel" to "office"
        )
        // 用户同意隐私政策后才发送 deviceId，避免在同意前访问 ANDROID_ID
        if (privacyConsentManager.isPrivacyConsented) {
            baseMap["deviceId"] = requestDeviceInfoProvider.getAppInstanceId()
        }
        val map: Map<String, Any> = baseMap
        val headerInfo = JSONObject(map).toString()

        // 请求头日志同样由 KLogger 脱敏，不改变实际加密内容。
        if (runtimeConfigProvider.isDebug) {
            logI(
                "【请求头加密前】请求头（日志已脱敏）: $headerInfo",
                tag = "RequestInterceptor",
            )
        }

        return mapOf(
            "AesKeyString" to getAKHead(randomString),
            "BaseParamString" to encryptRequest(randomString, headerInfo)
        )
    }

    private fun getAKHead(data: String): String {
        return requestCryptoProvider.encryptRsaToHex(data, runtimeConfigProvider.publicKey)
    }

    private fun encryptRequest(key: String, data: String): String {
        return encryptRequest(key = key, data = data.toByteArray())
    }

    private fun encryptRequest(key: String, data: ByteArray): String {
        return requestCryptoProvider.encryptAesToHex(data, key)
    }

    private fun generateRandomString(length: Int): String {
        if (length <= 0) return ""
        val chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
        val random = SecureRandom()
        return buildString(length) {
            repeat(length) {
                append(chars[random.nextInt(chars.length)])
            }
        }
    }

    private companion object {
        const val MAX_LOGGABLE_BODY_BYTES = 16 * 1024
    }
}
