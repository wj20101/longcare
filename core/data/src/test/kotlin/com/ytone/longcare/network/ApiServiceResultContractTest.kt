package com.ytone.longcare.network

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.TencentFaceApiService
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.network.SuppressSessionInvalidation
import com.ytone.longcare.di.DefaultOkHttpClient
import com.ytone.longcare.di.NetworkDataModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType
import kotlin.coroutines.Continuation

class ApiServiceResultContractTest {

    @Test
    fun `all suspend API methods return ApiResult so centralized mapping cannot be skipped`() {
        listOf(
            LongCareApiService::class.java,
            TencentFaceApiService::class.java,
        ).forEach { service ->
            val suspendMethods =
                service.declaredMethods.filter { method ->
                    method.parameterTypes.lastOrNull() == Continuation::class.java
                }
            assertTrue("${service.simpleName} must declare suspend endpoints", suspendMethods.isNotEmpty())

            suspendMethods.forEach { method ->
                val resultType = method.suspendResultType()
                assertTrue(
                    "${service.simpleName}.${method.name} must return ApiResult<T>",
                    resultType is ParameterizedType,
                )
                assertEquals(
                    "${service.simpleName}.${method.name} bypasses centralized API mapping",
                    ApiResult::class.java,
                    (resultType as ParameterizedType).rawType,
                )
            }
        }
    }

    @Test
    fun `login log is the only endpoint that suppresses session invalidation`() {
        val annotatedMethods =
            LongCareApiService::class.java.declaredMethods.filter { method ->
                method.getAnnotation(SuppressSessionInvalidation::class.java) != null
            }

        assertEquals(listOf("recordLoginLog"), annotatedMethods.map(Method::getName))
        assertNotNull(
            annotatedMethods.single()
                .getAnnotation(SuppressSessionInvalidation::class.java),
        )
    }

    @Test
    fun `Tencent Retrofit uses isolated client without LongCare interceptors or secret logging`() {
        val provider =
            NetworkDataModule::class.java.declaredMethods.single { method ->
                method.name == "provideTencentFaceRetrofit"
            }

        assertTrue(
            "Tencent Retrofit must use @DefaultOkHttpClient",
            provider.parameterAnnotations
                .first()
                .any { annotation ->
                    annotation.annotationClass.java == DefaultOkHttpClient::class.java
                },
        )
    }
}

private fun Method.suspendResultType(): Type {
    val continuationType = genericParameterTypes.last() as ParameterizedType
    val continuationArgument = continuationType.actualTypeArguments.single()
    return when (continuationArgument) {
        is WildcardType ->
            continuationArgument.lowerBounds.firstOrNull()
                ?: continuationArgument.upperBounds.single()

        else -> continuationArgument
    }
}
