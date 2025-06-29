package com.ytone.longcare.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 类型安全的导航路由定义
 * 使用 Kotlin Serialization 来确保参数传递的安全性
 */

@Keep
@Serializable
object LoginRoute

@Keep
@Serializable
object HomeRoute

@Keep
@Serializable
data class ServiceRoute(val orderId: Long)

@Keep
@Serializable
data class NursingExecutionRoute(val orderId: Long)

@Serializable
enum class SignInMode {
    START_ORDER, END_ORDER
}

@Keep
@Serializable
data class NfcSignInRoute(
    val orderId: Long, val signInMode: SignInMode, val endOrderParams: EndOderInfo? = null
)

@Keep
@Serializable
data class EndOderInfo(
    val projectIdList: List<Int> = emptyList(),
    val beginImgList: List<String> = emptyList(),
    val endImgList: List<String> = emptyList()
)

@Keep
@Serializable
data class SelectServiceRoute(val orderId: Long)

@Keep
@Serializable
data class PhotoUploadRoute(
    val orderId: Long, val projectIds: List<Int> = emptyList()
)

@Keep
@Serializable
object CarePlansListRoute

@Keep
@Serializable
object ServiceRecordsListRoute

@Keep
@Serializable
object TxFaceRoute

@Keep
@Serializable
object LocationTrackingRoute