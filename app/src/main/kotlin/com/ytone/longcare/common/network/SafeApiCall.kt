package com.ytone.longcare.common.network

import com.ytone.longcare.common.utils.logE
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
    return withContext(dispatcher) {
        try {
            val response = apiCall()

            if (response.isSuccess()) {
                // ==========================================================
                // 新增的核心调整：检查 data 是否为 null
                // ==========================================================
                val data = response.data
                if (data != null) {
                    // 业务成功，且 data 字段有值，返回成功结果。
                    ApiResult.Success(data)
                } else {
                    // 业务成功，但 data 字段为 null。
                    // 这可能是一种意外情况，我们将其视为一种特定的失败来处理，
                    // 以防止将 null 传递给期望非空数据的代码。
                    ApiResult.Failure(response.resultCode, "响应成功，但数据为空")
                }
            } else {
                // 业务失败，返回失败结果和错误信息。
                ApiResult.Failure(response.resultCode, response.resultMsg)
            }
        } catch (throwable: Throwable) {
            logE(message = "api fail" , throwable = throwable)
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