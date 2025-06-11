package com.ytone.longcare.common.network

import com.ytone.longcare.model.Response
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.IOException

/**
 * 一个安全的网络请求调用包装器。
 * @param dispatcher 执行网络请求的协程调度器，通常是 Dispatchers.IO。
 * @param apiCall 一个 suspend lambda，它代表了实际的 Retrofit API 调用。
 * @return 返回一个 ApiResult 对象，封装了成功、业务失败或异常的结果。
 */
suspend fun <T> safeApiCall(
    dispatcher: CoroutineDispatcher,
    apiCall: suspend () -> Response<T>
): ApiResult<T> {
    // 将网络请求切换到指定的IO线程
    return withContext(dispatcher) {
        try {
            // 执行实际的 API 调用
            val response = apiCall()

            // 检查业务状态码
            if (response.isSuccess()) { // isSuccess() 是您在 Response 类中定义的方法
                // 业务成功，返回成功结果和数据
                ApiResult.Success(response.data)
            } else {
                // 业务失败，返回失败结果和错误信息
                ApiResult.Failure(response.resultCode, response.resultMsg)
            }
        } catch (throwable: Throwable) {
            // 捕获所有可能的异常
            when (throwable) {
                is IOException -> {
                    // IO 异常，通常是网络连接问题（无网络、超时）
                    ApiResult.Exception(IOException("网络连接异常，请检查您的网络", throwable))
                }

                is HttpException -> {
                    // HTTP 异常，例如 404 Not Found, 500 Internal Server Error
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
                    // 其他未知异常，例如 JSON 解析错误等
                    ApiResult.Exception(Exception("未知错误", throwable))
                }
            }
        }
    }
}