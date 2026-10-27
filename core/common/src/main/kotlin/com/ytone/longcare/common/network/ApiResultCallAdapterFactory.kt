package com.ytone.longcare.common.network

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.result.SessionInvalidationCode
import com.squareup.moshi.Types
import com.ytone.longcare.model.Response as ApiResponse
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 标记无需触发全局会话失效事件的静默接口，例如尽力上报的登录日志。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class SuppressSessionInvalidation

/**
 * 仅用于业务成功时允许 data 为 null 或缺失的接口；返回值必须声明为 ApiResult<T?>。
 * 空响应体、解析异常及业务失败仍按原规则处理，不受此标记影响。
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class AllowEmptyData

/**
 * 项目标准 ApiResponse<T> -> ApiResult<T> 自动适配器。
 *
 * Retrofit Service 只要声明 suspend fun ...(): ApiResult<T>，HTTP、网络异常和业务码
 * 就会在恢复调用协程前统一完成映射，不需要 Repository 手动包 safeApiCall。
 */
class ApiResultCallAdapterFactory(
    private val sessionInvalidationHandler: SessionInvalidationHandler,
) : CallAdapter.Factory() {

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
            "ApiResult 返回类型必须包含数据泛型，例如 ApiResult<User>"
        }

        val dataType = getParameterUpperBound(0, callBodyType)
        val apiResponseType =
            Types.newParameterizedType(ApiResponse::class.java, dataType)
        val suppressSessionInvalidation =
            annotations.any { it is SuppressSessionInvalidation }

        return StandardApiResultCallAdapter(
            responseType = apiResponseType,
            sessionInvalidationHandler = sessionInvalidationHandler,
            suppressSessionInvalidation = suppressSessionInvalidation,
            allowEmptyData = annotations.any { it is AllowEmptyData },
        )
    }
}

private class StandardApiResultCallAdapter(
    private val responseType: Type,
    private val sessionInvalidationHandler: SessionInvalidationHandler,
    private val suppressSessionInvalidation: Boolean,
    private val allowEmptyData: Boolean,
) : CallAdapter<ApiResponse<Any?>, Call<ApiResult<Any?>>> {

    override fun responseType(): Type = responseType

    override fun adapt(call: Call<ApiResponse<Any?>>): Call<ApiResult<Any?>> =
        MappedApiResultCall(call) { response, request ->
            if (response.isSuccess()) {
                val data = response.data
                if (data != null || allowEmptyData) {
                    ApiResult.Success(data)
                } else {
                    ApiResult.Exception(ApiRequestErrorMapper.emptyData())
                }
            } else {
                if (
                    SessionInvalidationCode.requiresLogout(response.resultCode) &&
                    !suppressSessionInvalidation
                ) {
                    request.tag(RequestSessionGeneration::class.java)?.let {
                        sessionInvalidationHandler.invalidate(response.resultMsg, it.value)
                    }
                }
                ApiResult.Failure(
                    code = response.resultCode,
                    message = response.resultMsg,
                )
            }
        }
}
