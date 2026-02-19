package com.ytone.longcare.common.security

enum class KeySize(val bits: Int, val bytes: Int) {
    AES_128(128, 16),
    AES_192(192, 24),
    AES_256(256, 32),
    RSA_1024(1024, 128),
    RSA_2048(2048, 256),
    RSA_3072(3072, 384),
    RSA_4096(4096, 512),
}
