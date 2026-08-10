package com.ytone.longcare.common.network

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.TencentApiResponse
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 腾讯云直出响应 -> ApiResult<T> 自动适配器。
 */
class TencentApiResultCallAdapterFactory : CallAdapter.Factory() {

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit,
    ): CallAdapter<*, *>? {
        if (getRawType(returnType) != Call::class.java) {
            return null
        }
        require(returnType is ParameterizedType) {
            "Retrofit Call 返回类型必须包含泛型"
        }
        val callBodyType = getParameterUpperBound(0, returnType)
        if (getRawType(callBodyType) != ApiResult::class.java) {
            return null
        }
        require(callBodyType is ParameterizedType) {
            "ApiResult 返回类型必须包含数据泛型"
        }

        val dataType = getParameterUpperBound(0, callBodyType)
        require(TencentApiResponse::class.java.isAssignableFrom(getRawType(dataType))) {
            "腾讯云 ApiResult 数据类型必须实现 TencentApiResponse"
        }
        return TencentApiResultCallAdapter<TencentApiResponse>(dataType)
    }
}

private class TencentApiResultCallAdapter<T : TencentApiResponse>(
    private val responseType: Type,
) : CallAdapter<T, Call<ApiResult<T>>> {

    override fun responseType(): Type = responseType

    override fun adapt(call: Call<T>): Call<ApiResult<T>> =
        MappedApiResultCall(call) { response ->
            if (response.code == "0") {
                ApiResult.Success(response)
            } else {
                ApiResult.Failure(
                    code = response.code.toIntOrNull() ?: -1,
                    message = response.msg,
                )
            }
        }
}
