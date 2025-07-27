package com.ytone.longcare.navigation

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

/**
 * 类型安全的导航路由定义
 * 使用 Kotlin Serialization 来确保参数传递的安全性
 */

/**
 * 登录页面路由
 */
@Keep
@Serializable
object LoginRoute

/**
 * 主页路由
 */
@Keep
@Serializable
object HomeRoute

/**
 * 服务详情页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class ServiceRoute(val orderId: Long)

/**
 * 护理执行页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class NursingExecutionRoute(val orderId: Long)

/**
 * NFC签到模式枚举
 */
@Serializable
enum class SignInMode {
    /**
     * 开始订单模式
     */
    START_ORDER, 
    
    /**
     * 结束订单模式
     */
    END_ORDER
}

/**
 * NFC签到路由
 * @param orderId 订单ID
 * @param signInMode 签到模式（开始订单或结束订单）
 * @param endOrderParams 结束订单时的参数信息
 */
@Keep
@Serializable
data class NfcSignInRoute(
    val orderId: Long, val signInMode: SignInMode, val endOrderParams: EndOderInfo? = null
)

/**
 * 服务倒计时页面路由
 * @param orderId 订单ID
 * @param projectIdList 项目ID列表
 */
@Keep
@Serializable
data class ServiceCountdownRoute(
    val orderId: Long,
    val projectIdList: List<Int>
)

/**
 * 结束订单信息数据类
 * @param projectIdList 项目ID列表
 * @param beginImgList 开始时的图片列表
 * @param endImgList 结束时的图片列表
 */
@Keep
@Serializable
data class EndOderInfo(
    val projectIdList: List<Int> = emptyList(),
    val beginImgList: List<String> = emptyList(),
    val endImgList: List<String> = emptyList()
)

/**
 * 选择服务页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class SelectServiceRoute(val orderId: Long)

/**
 * 照片上传页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class PhotoUploadRoute(val orderId: Long)

/**
 * 护理计划列表页面路由
 */
@Keep
@Serializable
object CarePlansListRoute

/**
 * 服务记录列表页面路由
 */
@Keep
@Serializable
object ServiceRecordsListRoute

/**
 * 腾讯人脸识别路由
 */
@Keep
@Serializable
object TxFaceRoute

/**
 * 位置追踪页面路由
 */
@Keep
@Serializable
object LocationTrackingRoute

/**
 * 服务完成页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class ServiceCompleteRoute(val orderId: Long)

/**
 * 人脸识别引导页面路由
 */
@Keep
@Serializable
data class FaceRecognitionGuideRoute(val orderId: Long)

/**
 * 选择设备页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class SelectDeviceRoute(val orderId: Long)

/**
 * 身份认证页面路由
 * @param orderId 订单ID
 */
@Keep
@Serializable
data class IdentificationRoute(val orderId: Long)

/**
 * 已服务工时用户列表页面路由
 */
@Keep
@Serializable
object HaveServiceUserListRoute

/**
 * 未服务工时用户列表页面路由
 */
@Keep
@Serializable
object NoServiceUserListRoute