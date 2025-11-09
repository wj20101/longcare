package com.ytone.longcare.network.processor

import okhttp3.Response

/**
 * 响应处理器接口
 * 每个需要特殊处理的API接口实现自己的处理器
 */
interface ResponseProcessor {
    
    /**
     * 判断是否可以处理该响应
     * 
     * @param path 请求路径
     * @return true表示可以处理，false表示不处理
     */
    fun canProcess(path: String): Boolean
    
    /**
     * 处理响应
     * 
     * @param response 原始响应
     * @param aesKey 当前请求的AES密钥
     * @return 处理后的响应，如果处理失败返回原始响应
     */
    fun process(response: Response, aesKey: String?): Response
}
