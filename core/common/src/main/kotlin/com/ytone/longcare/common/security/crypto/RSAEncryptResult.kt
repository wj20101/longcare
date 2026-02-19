package com.ytone.longcare.common.security

import android.util.Base64

data class RSAEncryptResult(
    val cipherText: ByteArray,
) {
    fun getCipherTextBase64(): String = Base64.encodeToString(cipherText, Base64.NO_WRAP)

    fun getCipherTextHex(): String = CryptoUtils.bytesToHex(cipherText)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RSAEncryptResult
        return cipherText.contentEquals(other.cipherText)
    }

    override fun hashCode(): Int = cipherText.contentHashCode()
}
