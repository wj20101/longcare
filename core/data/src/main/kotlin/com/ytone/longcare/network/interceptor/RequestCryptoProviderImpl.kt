package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.security.AESMode
import com.ytone.longcare.common.security.CryptoUtils
import com.ytone.longcare.common.security.RSAMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RequestCryptoProviderImpl @Inject constructor() : RequestCryptoProvider {
    override fun encryptAesToHex(data: ByteArray, key: String): String {
        return CryptoUtils.aesEncrypt(
            plainData = data,
            keyString = key,
            mode = AESMode.CBC_PKCS7_PADDING,
            iv = if (key.length > 16) CryptoUtils.getInitializationVectorConcise() else key.toByteArray()
        )?.getCipherTextHex().orEmpty()
    }

    override fun encryptRsaToHex(data: String, publicKey: String): String {
        return CryptoUtils.rsaEncrypt(data, publicKey, RSAMode.NONE_PKCS1_PADDING)
            ?.getCipherTextHex()
            .orEmpty()
    }
}

