package com.ytone.longcare.common.security

enum class AESMode(val transformation: String, val requiresIV: Boolean = true) {
    GCM_NO_PADDING("AES/GCM/NoPadding", true),
    CBC_PKCS5_PADDING("AES/CBC/PKCS5Padding", true),
    CBC_PKCS7_PADDING("AES/CBC/PKCS7Padding", true),
    ECB_PKCS5_PADDING("AES/ECB/PKCS5Padding", false),
    ECB_PKCS7_PADDING("AES/ECB/PKCS7Padding", false),
    CFB_PKCS5_PADDING("AES/CFB/PKCS5Padding", true),
    CFB_PKCS7_PADDING("AES/CFB/PKCS7Padding", true),
    CFB_NO_PADDING("AES/CFB/NoPadding", true),
    OFB_PKCS5_PADDING("AES/OFB/PKCS5Padding", true),
    OFB_PKCS7_PADDING("AES/OFB/PKCS7Padding", true),
    OFB_NO_PADDING("AES/OFB/NoPadding", true),
    CTR_NO_PADDING("AES/CTR/NoPadding", true),
}
