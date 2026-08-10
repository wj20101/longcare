package com.ytone.longcare.network.processor

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.model.Response as ApiResponse
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SystemConfigResponseProcessorTest {

    private val moshi = Moshi.Builder().build()

    @Test
    fun `decrypt exception keeps original response body readable`() {
        val processor =
            SystemConfigResponseProcessor(
                moshi = moshi,
                thirdKeyDecryptor =
                    object : ThirdKeyDecryptor {
                        override fun decryptThirdKeyStr(
                            encryptedThirdKeyStr: String,
                            aesKey: String,
                        ): ThirdKeyReturnModel = error("decrypt failed")
                    },
            )

        val processed = processor.process(successResponse(ORIGINAL_BODY), aesKey = "aes-key")

        assertEquals(ORIGINAL_BODY, processed.body.string())
    }

    @Test
    fun `successful decrypt replaces only third key payload`() {
        val decrypted =
            ThirdKeyReturnModel(
                gaoDeMapApiKey = "map-key",
                txFaceAppId = "face-app-id",
                txFaceAppSecret = "face-secret",
                txFaceAppLicence = "face-licence",
            )
        val processor =
            SystemConfigResponseProcessor(
                moshi = moshi,
                thirdKeyDecryptor =
                    object : ThirdKeyDecryptor {
                        override fun decryptThirdKeyStr(
                            encryptedThirdKeyStr: String,
                            aesKey: String,
                        ): ThirdKeyReturnModel = decrypted
                    },
            )

        val processed = processor.process(successResponse(ORIGINAL_BODY), aesKey = "aes-key")
        val parsed = requireNotNull(responseAdapter().fromJson(processed.body.string()))

        val decryptedPayload =
            requireNotNull(parsed.data?.thirdKeyStr).let { thirdKeyJson ->
                moshi.adapter(ThirdKeyReturnModel::class.java).fromJson(thirdKeyJson)
            }
        assertEquals(decrypted, decryptedPayload)
        assertEquals("LongCare", parsed.data?.companyName)
    }

    private fun responseAdapter() =
        moshi.adapter<ApiResponse<SystemConfigModel>>(
            Types.newParameterizedType(ApiResponse::class.java, SystemConfigModel::class.java)
        )

    private fun successResponse(body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://careapi.ytone.cn/V1/System/Config").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()

    private companion object {
        const val ORIGINAL_BODY =
            "{\"resultCode\":1000,\"resultMsg\":\"success\",\"data\":{\"companyName\":\"LongCare\",\"thirdKeyStr\":\"encrypted-value\"}}"
    }
}
