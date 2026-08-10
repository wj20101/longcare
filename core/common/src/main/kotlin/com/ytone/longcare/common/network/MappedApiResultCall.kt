package com.ytone.longcare.common.network

import com.ytone.longcare.model.result.ApiResult
import kotlinx.coroutines.CancellationException
import okhttp3.Request
import okio.Timeout
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException

/**
 * 把 Retrofit 的传输结果统一转换为 [ApiResult]。
 *
 * 只有声明返回 ApiResult 的接口会使用该 Call；返回原始 Response 的下载等接口不受影响。
 */
internal class MappedApiResultCall<Raw, Data>(
    private val delegate: Call<Raw>,
    private val responseMapper: (Raw) -> ApiResult<Data>,
) : Call<ApiResult<Data>> {

    override fun enqueue(callback: Callback<ApiResult<Data>>) {
        delegate.enqueue(
            object : Callback<Raw> {
                override fun onResponse(
                    call: Call<Raw>,
                    response: Response<Raw>,
                ) {
                    callback.onResponse(
                        this@MappedApiResultCall,
                        mapResponse(response),
                    )
                }

                override fun onFailure(
                    call: Call<Raw>,
                    throwable: Throwable,
                ) {
                    if (
                        delegate.isCanceled ||
                        throwable is CancellationException ||
                        throwable !is Exception
                    ) {
                        callback.onFailure(this@MappedApiResultCall, throwable)
                        return
                    }
                    callback.onResponse(
                        this@MappedApiResultCall,
                        Response.success(
                            ApiResult.Exception(
                                ApiRequestErrorMapper.fromThrowable(throwable)
                            )
                        ),
                    )
                }
            }
        )
    }

    override fun execute(): Response<ApiResult<Data>> =
        try {
            mapResponse(delegate.execute())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: IOException) {
            if (delegate.isCanceled) {
                throw exception
            }
            Response.success(
                ApiResult.Exception(
                    ApiRequestErrorMapper.fromThrowable(exception)
                )
            )
        } catch (exception: RuntimeException) {
            Response.success(
                ApiResult.Exception(
                    ApiRequestErrorMapper.fromThrowable(exception)
                )
            )
        }

    private fun mapResponse(response: Response<Raw>): Response<ApiResult<Data>> {
        val result =
            if (!response.isSuccessful) {
                ApiResult.Exception(
                    ApiRequestErrorMapper.fromHttp(response.code())
                )
            } else {
                val body = response.body()
                if (body == null) {
                    ApiResult.Exception(ApiRequestErrorMapper.emptyBody())
                } else {
                    try {
                        responseMapper(body)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        ApiResult.Exception(
                            ApiRequestErrorMapper.fromThrowable(exception)
                        )
                    }
                }
            }
        return Response.success(result)
    }

    override fun isExecuted(): Boolean = delegate.isExecuted

    override fun cancel() = delegate.cancel()

    override fun isCanceled(): Boolean = delegate.isCanceled

    override fun clone(): Call<ApiResult<Data>> =
        MappedApiResultCall(
            delegate = delegate.clone(),
            responseMapper = responseMapper,
        )

    override fun request(): Request = delegate.request()

    override fun timeout(): Timeout = delegate.timeout()
}
