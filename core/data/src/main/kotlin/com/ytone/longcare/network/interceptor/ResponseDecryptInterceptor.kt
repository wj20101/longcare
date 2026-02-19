package com.ytone.longcare.network.interceptor

import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.network.processor.ResponseProcessor
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * 响应解密拦截器
 * 作为调度器，根据URL路径分发给对应的响应处理器
 * 
 * 设计原则：
 * 1. 低耦合：每个接口的处理逻辑独立在各自的Processor中
 * 2. 易扩展：新增接口处理只需实现ResponseProcessor接口并注册
 * 3. 单一职责：拦截器只负责调度，不包含具体业务逻辑
 * 4. 安全性：使用OkHttp的tag机制传递密钥，避免内存泄漏
 */
class ResponseDecryptInterceptor @Inject constructor(
    private val processors: Set<@JvmSuppressWildcards ResponseProcessor>
) : Interceptor {

    companion object {
        private const val TAG = "ResponseDecryptInterceptor"
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        try {
            // 获取请求路径
            val path = request.url.encodedPath
            
            // 查找可以处理该路径的处理器
            val processor = processors.firstOrNull { it.canProcess(path) }
            
            if (processor != null) {
                logD(TAG, "Found processor for path: $path")
                
                // 从请求的tag中获取AES密钥
                // 使用OkHttp的tag机制，密钥只存在于当前请求的生命周期内
                // 请求完成后自动释放，无需手动清理
                val aesKeyTag = request.tag(AesKeyTag::class.java)
                val aesKey = aesKeyTag?.key
                
                // 使用处理器处理响应
                return processor.process(response, aesKey)
            }
            
            // 没有找到处理器，返回原始响应
            return response
            
        } catch (e: Exception) {
            logE(message = "响应解密调度异常，返回原始响应: ${request.url.encodedPath}", throwable = e)
            // 发生异常时返回原始响应
            return response
        }
    }
}
