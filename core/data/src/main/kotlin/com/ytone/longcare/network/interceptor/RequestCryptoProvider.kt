package com.ytone.longcare.network.interceptor

/**
 * 请求加密能力抽象。
 * 由 app 层提供具体实现，避免 core:data 直接依赖 app 的安全工具实现。
 */
interface RequestCryptoProvider {
    fun encryptAesToHex(data: ByteArray, key: String): String
    fun encryptRsaToHex(data: String, publicKey: String): String
}

