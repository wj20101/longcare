package com.ytone.longcare.network.interceptor

import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.security.AESMode
import com.ytone.longcare.common.security.CryptoUtils
import com.ytone.longcare.common.security.RSAMode
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.common.utils.RandomUtils
import com.ytone.longcare.common.utils.TimeUtils
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.toJsonStringMap
import com.ytone.longcare.domain.repository.UserSessionRepository
import okhttp3.Interceptor
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import javax.inject.Inject

class RequestInterceptor @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val deviceUtils: DeviceUtils
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val requestBody = request.body
        if (!url.toString().startsWith(BuildConfig.BASE_URL)) {
            return chain.proceed(request)
        }

        val method = request.method
        val newRequestBuilder = request.newBuilder()
        val randomString = RandomUtils.generateRandomStringKotlin(32)
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
                val encryptRequest = encryptRequest(randomString, requestBodyBytes)
                val bodyMap = mapOf("ParamJsonString" to encryptRequest)
                val encryptData = bodyMap.toJsonStringMap().orEmpty()
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
        val map = mapOf<String, Any>(
            "userId" to (userSessionRepository.sessionState.value.user?.userId ?: 0),
            "token" to userSessionRepository.sessionState.value.user?.token.orEmpty(),
            "accountId" to (userSessionRepository.sessionState.value.user?.accountId ?: 0),
            "companyId" to (userSessionRepository.sessionState.value.user?.companyId ?: 0),
            "userIdentity" to (userSessionRepository.sessionState.value.user?.userIdentity ?: 0),
            "nonce" to randomString,
            "timeSpan" to TimeUtils.getCurrentEpochMilliseconds(),
            "platform" to "android",
            "versionCode" to deviceUtils.getAppVersionCode(),
            "versionName" to deviceUtils.getAppVersionName(),
            "deviceId" to deviceUtils.getAppInstanceId(),
            "channel" to "office"
        )
        val headerInfo = map.toJsonStringMap().orEmpty()
        return mapOf(
            "AesKeyString" to getAKHead(randomString),
            "BaseParamString" to encryptRequest(randomString, headerInfo)
        )
    }

    private fun getAKHead(data: String): String {
        return CryptoUtils.rsaEncrypt(data, BuildConfig.PUBLIC_KEY, RSAMode.NONE_PKCS1_PADDING)
            ?.getCipherTextHex().orEmpty()
    }

    private fun encryptRequest(key: String, data: String): String {
        return encryptRequest(key = key, data = data.toByteArray())
    }

    private fun encryptRequest(key: String, data: ByteArray): String {
        return CryptoUtils.aesEncrypt(
            data,
            key,
            AESMode.CBC_PKCS7_PADDING,
            if (key.length > 16) getInitializationVectorConcise() else key.toByteArray()
        )?.getCipherTextHex().orEmpty()
    }

    private fun getInitializationVectorConcise(): ByteArray {
        return byteArrayOf(
            0x41.toByte(),
            0x72.toByte(),
            0x65.toByte(),
            0x79.toByte(),
            0x6F.toByte(),
            0x75.toByte(),
            0x6D.toByte(),
            0x79.toByte(),
            0x53.toByte(),
            0x6E.toByte(),
            0x6F.toByte(),
            0x77.toByte(),
            0x6D.toByte(),
            0x61.toByte(),
            0x6E.toByte(),
            0x3F.toByte()
        )
    }
}