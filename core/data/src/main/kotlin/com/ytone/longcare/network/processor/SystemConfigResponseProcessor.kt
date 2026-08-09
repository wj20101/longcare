package com.ytone.longcare.network.processor

import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel
import com.ytone.longcare.model.Response as ApiResponse
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SystemConfig接口响应处理器
 * 负责解密thirdKeyStr字段
 */
@Singleton
class SystemConfigResponseProcessor @Inject constructor(
    private val moshi: Moshi,
    private val thirdKeyDecryptor: ThirdKeyDecryptor,
) : ResponseProcessor {
    
    companion object {
        private const val TAG = "SystemConfigProcessor"
        private const val SYSTEM_CONFIG_PATH = "/V1/System/Config"
        private const val MAX_PEEK_BYTES = 1_048_576L
    }
    
    // 创建Moshi适配器
    private val responseAdapter by lazy {
        val type = Types.newParameterizedType(ApiResponse::class.java, SystemConfigModel::class.java)
        moshi.adapter<ApiResponse<SystemConfigModel>>(type)
    }
    
    private val thirdKeyAdapter by lazy {
        moshi.adapter(ThirdKeyReturnModel::class.java)
    }
    
    override fun canProcess(path: String): Boolean {
        return path.endsWith(SYSTEM_CONFIG_PATH)
    }
    
    override fun process(response: Response, aesKey: String?): Response {
        // 检查AES密钥
        if (aesKey.isNullOrEmpty()) {
            logE(TAG, "AES key not found")
            return response
        }
        
        // 检查响应是否成功
        if (!response.isSuccessful) {
            return response
        }
        
        try {
            // Parse a non-consuming snapshot so every unchanged/error path can return the original body.
            val responseBody = response.body
            val responseString = response.peekBody(MAX_PEEK_BYTES).string()
            
            // 使用Moshi解析响应
            val apiResponse = responseAdapter.fromJson(responseString)
            
            if (apiResponse == null) {
                logE(TAG, "Failed to parse response")
                return response
            }
            
            // 检查data字段
            val systemConfig = apiResponse.data
            if (systemConfig == null) {
                logD(TAG, "No data in response")
                return response
            }
            
            // 检查thirdKeyStr字段
            val encryptedThirdKeyStr = systemConfig.thirdKeyStr
            if (encryptedThirdKeyStr.isEmpty()) {
                logD(TAG, "thirdKeyStr is empty, no decryption needed")
                return response
            }
            
            // 解密thirdKeyStr
            val decryptedModel = thirdKeyDecryptor.decryptThirdKeyStr(
                encryptedThirdKeyStr = encryptedThirdKeyStr,
                aesKey = aesKey
            )
            
            if (decryptedModel != null) {
                logD(TAG, "Successfully decrypted thirdKeyStr")
                
                // 将解密后的对象转换为JSON字符串
                val decryptedJson = thirdKeyAdapter.toJson(decryptedModel)
                
                // 创建新的SystemConfig对象，替换thirdKeyStr
                val updatedSystemConfig = systemConfig.copy(thirdKeyStr = decryptedJson)
                
                // 创建新的ApiResponse对象
                val updatedApiResponse = apiResponse.copy(data = updatedSystemConfig)
                
                // 序列化回JSON
                val updatedResponseString = responseAdapter.toJson(updatedApiResponse)
                
                // 创建新的响应体
                val newResponseBody = updatedResponseString.toResponseBody(responseBody.contentType())
                
                return response.newBuilder()
                    .body(newResponseBody)
                    .build()
            } else {
                logE(TAG, "Failed to decrypt thirdKeyStr, returning original response")
                return response
            }
            
        } catch (e: Exception) {
            logE(TAG, "Error processing response", e)
            return response
        }
    }
}
