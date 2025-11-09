package com.ytone.longcare.common.utils

import android.util.Base64
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.response.ThirdKeyReturnModel
import com.ytone.longcare.common.security.AESMode
import com.ytone.longcare.common.security.CryptoUtils

/**
 * 第三方密钥解密工具类
 * 用于解密SystemConfig中的thirdKeyStr字段
 */
object ThirdKeyDecryptUtils {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)

    /**
     * 解密thirdKeyStr字段
     * 
     * @param encryptedThirdKeyStr 加密的第三方密钥字符串（16进制格式）
     * @param aesKey AES密钥（来自请求头的AESKEY）
     * @return 解密后的ThirdKeyReturnModel对象，解密失败返回null
     */
    fun decryptThirdKeyStr(encryptedThirdKeyStr: String, aesKey: String): ThirdKeyReturnModel? {
        if (encryptedThirdKeyStr.isEmpty() || aesKey.isEmpty()) {
            logE("ThirdKeyDecryptUtils", "encryptedThirdKeyStr or aesKey is empty")
            return null
        }

        return try {
            // 将AES密钥转换为Base64格式（CryptoUtils需要Base64格式的密钥）
            val aesKeyBase64 = Base64.encodeToString(aesKey.toByteArray(), Base64.NO_WRAP)
            
            // 使用AES解密（CBC模式，PKCS7填充）
            // 注意：根据RequestInterceptor的实现，使用的是CBC_PKCS7_PADDING模式
            val decryptedJson = CryptoUtils.aesDecryptFromHex(
                hexCipherText = encryptedThirdKeyStr,
                keyString = aesKeyBase64,
                mode = AESMode.CBC_PKCS7_PADDING,
                hexIV = null // IV会从密文中提取
            )

            if (decryptedJson.isNullOrEmpty()) {
                logE("ThirdKeyDecryptUtils", "Failed to decrypt thirdKeyStr")
                return null
            }

            // 解析JSON字符串为ThirdKeyReturnModel对象
            adapter.fromJson(decryptedJson)
        } catch (e: Exception) {
            logE("ThirdKeyDecryptUtils", "Error decrypting thirdKeyStr", e)
            null
        }
    }

    /**
     * 加密ThirdKeyReturnModel对象（用于测试）
     * 
     * @param thirdKeyModel ThirdKeyReturnModel对象
     * @param aesKey AES密钥
     * @return 加密后的16进制字符串，加密失败返回null
     */
    fun encryptThirdKeyModel(thirdKeyModel: ThirdKeyReturnModel, aesKey: String): String? {
        if (aesKey.isEmpty()) {
            logE("ThirdKeyDecryptUtils", "aesKey is empty")
            return null
        }

        return try {
            // 将对象转换为JSON字符串
            val json = adapter.toJson(thirdKeyModel)
            
            // 将AES密钥转换为Base64格式
            val aesKeyBase64 = Base64.encodeToString(aesKey.toByteArray(), Base64.NO_WRAP)
            
            // 获取IV（与RequestInterceptor保持一致）
            val iv = getInitializationVectorConcise()
            
            // 使用AES加密
            val encryptResult = CryptoUtils.aesEncrypt(
                plainText = json,
                keyString = aesKeyBase64,
                mode = AESMode.CBC_PKCS7_PADDING,
                iv = iv
            )

            encryptResult?.getCombinedHex()
        } catch (e: Exception) {
            logE("ThirdKeyDecryptUtils", "Error encrypting thirdKeyModel", e)
            null
        }
    }

    /**
     * 获取初始化向量（与RequestInterceptor保持一致）
     */
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
