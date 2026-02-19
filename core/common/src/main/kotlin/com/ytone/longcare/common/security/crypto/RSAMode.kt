package com.ytone.longcare.common.security

enum class RSAMode(val transformation: String) {
    NONE_PKCS1_PADDING("RSA/NONE/PKCS1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 11
    },
    ECB_PKCS1_PADDING("RSA/ECB/PKCS1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 11
    },
    ECB_OAEP_WITH_SHA1_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-1AndMGF1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 20 - 2
    },
    ECB_OAEP_WITH_SHA256_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-256AndMGF1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 32 - 2
    },
    ECB_OAEP_WITH_SHA384_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-384AndMGF1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 48 - 2
    },
    ECB_OAEP_WITH_SHA512_AND_MGF1_PADDING("RSA/ECB/OAEPWithSHA-512AndMGF1Padding") {
        override fun getMaxDataSize(keySizeInBits: Int): Int = (keySizeInBits / 8) - 2 * 64 - 2
    };

    abstract fun getMaxDataSize(keySizeInBits: Int): Int

    companion object {
        val DEFAULT_ENCRYPTION_MODE = ECB_OAEP_WITH_SHA256_AND_MGF1_PADDING
    }
}
