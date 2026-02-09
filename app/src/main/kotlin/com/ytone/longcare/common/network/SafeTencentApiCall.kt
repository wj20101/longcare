package com.ytone.longcare.common.network

import com.ytone.longcare.api.response.TencentApiResponse
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * 腾讯云API专用的安全网络请求调用包装器（通用版本）
 * 腾讯云API的响应格式与项目标准Response<T>不同，成功码为"0"而非1000
 * 
 * @param T 腾讯云API响应类型，必须实现TencentApiResponse接口
 * @param dispatcher 执行网络请求的协程调度器，通常是 Dispatchers.IO
 * @param eventBus 用于发送特殊事件
 * @param apiCall 一个 suspend lambda，代表实际的腾讯云 API 调用
 * @param logTag 日志标签，用于区分不同的API调用
 * @return 返回一个 ApiResult 对象，封装了成功、业务失败或异常的结果
 */
suspend fun <T : TencentApiResponse> safeTencentApiCall(
    dispatcher: CoroutineDispatcher,
    eventBus: AppEventBus,
    logTag: String = "tencent api",
    apiCall: suspend () -> T
): ApiResult<T> {
    return withContext(dispatcher) {
        try {
            val response = apiCall()
            
            // 腾讯云API成功码为"0"
            if (response.code == "0") {
                ApiResult.Success(response)
            } else {
                // 业务失败，返回失败结果和错误信息
                ApiResult.Failure(
                    code = response.code.toIntOrNull() ?: -1,
                    message = response.msg
                )
            }
        } catch (throwable: Throwable) {
            if (throwable is CancellationException) {
                throw throwable
            }
            logE(message = "$logTag fail", throwable = throwable)
            // 捕获所有可能的异常
            when (throwable) {
                is IOException -> {
                    ApiResult.Exception(IOException("网络连接异常，请检查您的网络", throwable))
                }
                
                is HttpException -> {
                    val code = throwable.code()
                    val errorMsg = throwable.message()
                    ApiResult.Exception(
                        IOException(
                            "服务器异常 (Code: $code): $errorMsg",
                            throwable
                        )
                    )
                }
                
                else -> {
                    ApiResult.Exception(Exception("未知错误: ${throwable.message}", throwable))
                }
            }
        }
    }
}
