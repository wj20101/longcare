package com.ytone.longcare.common.security

import android.util.Base64

data class AESEncryptResult(
    val cipherText: ByteArray,
    val iv: ByteArray?,
) {
    fun getCipherTextBase64(): String = Base64.encodeToString(cipherText, Base64.NO_WRAP)

    fun getCipherTextHex(): String = CryptoUtils.bytesToHex(cipherText)

    fun getIvBase64(): String? = iv?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    fun getIvHex(): String? = iv?.let { CryptoUtils.bytesToHex(it) }

    fun getCombinedBase64(): String = if (iv != null) {
        Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    } else {
        Base64.encodeToString(cipherText, Base64.NO_WRAP)
    }

    fun getCombinedHex(): String = if (iv != null) {
        CryptoUtils.bytesToHex(iv + cipherText)
    } else {
        CryptoUtils.bytesToHex(cipherText)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AESEncryptResult
        if (!cipherText.contentEquals(other.cipherText)) return false
        if (iv != null) {
            if (other.iv == null) return false
            if (!iv.contentEquals(other.iv)) return false
        } else if (other.iv != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = cipherText.contentHashCode()
        result = 31 * result + (iv?.contentHashCode() ?: 0)
        return result
    }
}
