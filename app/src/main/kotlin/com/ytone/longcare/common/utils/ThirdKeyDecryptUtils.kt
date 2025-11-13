package com.ytone.longcare.common.utils

import com.squareup.moshi.Moshi
import com.ytone.longcare.api.response.ThirdKeyReturnModel
import com.ytone.longcare.common.security.AESMode
import com.ytone.longcare.common.security.CryptoUtils
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 第三方密钥解密工具类
 * 用于解密SystemConfig中的thirdKeyStr字段
 * 
 * 解密逻辑与RequestInterceptor的encryptRequest方法保持一致：
 * - 使用AES/CBC/PKCS7Padding模式
 * - 密钥直接使用原始字符串的字节（不经过Base64编码）
 * - IV根据密钥长度确定：长度>16使用固定IV，否则使用密钥本身
 * - 密文为16进制格式，不包含IV
 */
object ThirdKeyDecryptUtils {

    private val moshi = Moshi.Builder().build()
    private val adapter = moshi.adapter(ThirdKeyReturnModel::class.java)

    /**
     * 解密thirdKeyStr字段
     * 
     * @param encryptedThirdKeyStr 加密的第三方密钥字符串（16进制格式）
     * @param aesKey AES密钥（原始字符串，32字节）
     * @return 解密后的ThirdKeyReturnModel对象，解密失败返回null
     */
    fun decryptThirdKeyStr(encryptedThirdKeyStr: String, aesKey: String): ThirdKeyReturnModel? {
        if (encryptedThirdKeyStr.isEmpty() || aesKey.isEmpty()) {
            logE("ThirdKeyDecryptUtils", "encryptedThirdKeyStr or aesKey is empty")
            return null
        }

        return try {
            // 将16进制密文转换为字节数组
            val cipherBytes = CryptoUtils.hexToBytes(encryptedThirdKeyStr)
            
            // 确定IV：根据RequestInterceptor的逻辑
            // 如果密钥长度 > 16，使用固定的IV；否则使用密钥本身作为IV
            val iv = if (aesKey.length > 16) {
                CryptoUtils.getInitializationVectorConcise()
            } else {
                aesKey.toByteArray()
            }
            
            // 直接使用原始密钥字节（与RequestInterceptor的encryptRequest保持一致）
            val keyBytes = aesKey.toByteArray()
            require(keyBytes.size in listOf(16, 24, 32)) { 
                "Invalid AES key size: ${keyBytes.size * 8} bits" 
            }
            
            // 创建密钥规范
            val secretKey = SecretKeySpec(keyBytes, "AES")
            
            // 创建密码器
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            val ivSpec = IvParameterSpec(iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            // 解密
            val decryptedBytes = cipher.doFinal(cipherBytes)
            val decryptedJson = String(decryptedBytes, Charsets.UTF_8)

            if (decryptedJson.isEmpty()) {
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
     * @param aesKey AES密钥（原始字符串）
     * @return 加密后的16进制字符串（仅密文，不包含IV），加密失败返回null
     */
    fun encryptThirdKeyModel(thirdKeyModel: ThirdKeyReturnModel, aesKey: String): String? {
        if (aesKey.isEmpty()) {
            logE("ThirdKeyDecryptUtils", "aesKey is empty")
            return null
        }

        return try {
            // 将对象转换为JSON字符串
            val json = adapter.toJson(thirdKeyModel)
            
            // 确定IV：根据RequestInterceptor的逻辑
            // 如果密钥长度 > 16，使用固定的IV；否则使用密钥本身作为IV
            val iv = if (aesKey.length > 16) {
                CryptoUtils.getInitializationVectorConcise()
            } else {
                aesKey.toByteArray()
            }
            
            // 使用AES加密（直接使用原始密钥字符串，不转Base64）
            val encryptResult = CryptoUtils.aesEncrypt(
                plainText = json,
                keyString = aesKey,
                mode = AESMode.CBC_PKCS7_PADDING,
                iv = iv
            )

            // 返回仅密文的16进制字符串（与RequestInterceptor的encryptRequest保持一致）
            encryptResult?.getCipherTextHex()
        } catch (e: Exception) {
            logE("ThirdKeyDecryptUtils", "Error encrypting thirdKeyModel", e)
            null
        }
    }

    /**
     * 获取初始化向量（与RequestInterceptor保持一致）
     */
    
}
