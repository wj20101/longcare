package com.ytone.longcare.network.interceptor

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES密钥管理器
 * 用于在请求和响应拦截器之间共享AES密钥
 * 
 * 使用ThreadLocal确保线程安全，每个请求线程都有自己的密钥副本
 */
@Singleton
class AesKeyManager @Inject constructor() {
    
    private val keyStorage = ThreadLocal<String>()
    
    /**
     * 保存当前请求的AES密钥
     * 
     * @param key AES密钥（32字节随机字符串）
     */
    fun saveKey(key: String) {
        keyStorage.set(key)
    }
    
    /**
     * 获取当前请求的AES密钥
     * 
     * @return AES密钥，如果不存在返回null
     */
    fun getKey(): String? {
        return keyStorage.get()
    }
    
    /**
     * 清除当前请求的AES密钥
     * 应该在响应处理完成后调用
     */
    fun clearKey() {
        keyStorage.remove()
    }
}
