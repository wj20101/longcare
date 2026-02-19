package com.ytone.longcare.network.interceptor

/**
 * AES密钥标签
 * 用于在OkHttp请求中传递AES密钥
 * 
 * 使用OkHttp的tag机制，密钥只存在于单个请求的生命周期内
 * 请求完成后自动释放，无需手动清理，避免内存泄漏
 */
data class AesKeyTag(val key: String)
