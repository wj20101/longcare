package com.ytone.longcare.common.security

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CryptoUtilsTest {

    @Test
    fun generateAESKey_returnsBase64WithExpectedByteLength() {
        val encoded = CryptoUtils.generateAESKey(KeySize.AES_256)
        assertNotNull(encoded)

        val decoded = Base64.getDecoder().decode(encoded)
        assertEquals(KeySize.AES_256.bytes, decoded.size)
    }

    @Test
    fun generateAESKey_withNonAesKeySize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.generateAESKey(KeySize.RSA_2048)
        }
    }

    @Test
    fun generateRSAKeyPair_returnsKeyPairAndSupportsEncryptDecryptRoundTrip() {
        val keyPair = CryptoUtils.generateRSAKeyPair(KeySize.RSA_2048)
        assertNotNull(keyPair)

        val (publicKey, privateKey) = keyPair!!
        val plainText = "longcare-crypto-roundtrip"
        val encrypted = CryptoUtils.rsaEncrypt(plainText, publicKey)
        assertNotNull(encrypted)

        val decrypted = CryptoUtils.rsaDecrypt(encrypted!!.getCipherTextBase64(), privateKey)
        assertEquals(plainText, decrypted)
    }

    @Test
    fun generateRSAKeyPair_withNonRsaKeySize_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            CryptoUtils.generateRSAKeyPair(KeySize.AES_128)
        }
    }
}

