package com.ytone.longcare.navigation

import androidx.annotation.Keep
import com.ytone.longcare.model.WatermarkData
import kotlinx.serialization.Serializable

/**
 * 类型安全的导航路由定义
 * 使用 Kotlin Serialization 来确保参数传递的安全性
 * 
 * 设计原则：只传递orderId，通过UnifiedOrderRepository获取完整数据
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
 * Home 子图路由
 * 用于承载主页及其共享状态页面。
 */
@Keep
@Serializable
object HomeGraphRoute

/**
 * 服务详情页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class ServiceRoute(val orderParams: OrderNavParams)

/**
 * 护理执行页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class NursingExecutionRoute(val orderParams: OrderNavParams)


/**
 * WebView页面路由
 * @param url 要加载的网页URL
 * @param title 页面标题
 */
@Keep
@Serializable
data class WebViewRoute(
    val url: String,
    val title: String
)

/**
 * 选择服务页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class SelectServiceRoute(val orderParams: OrderNavParams)

/**
 * 照片上传页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class PhotoUploadRoute(val orderParams: OrderNavParams)

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
 * 人脸识别引导页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class FaceRecognitionGuideRoute(val orderParams: OrderNavParams)

/**
 * 选择设备页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class SelectDeviceRoute(val orderParams: OrderNavParams)

/**
 * 身份认证页面路由
 * @param orderParams 订单导航参数
 */
@Keep
@Serializable
data class IdentificationRoute(val orderParams: OrderNavParams)

/**
 * 用户列表页面路由
 * @param listType 列表类型：HAVE_SERVICE(已服务工时)、NO_SERVICE(未服务工时)、SERVICE_COUNT(服务次数)
 */
@Keep
@Serializable
data class UserListRoute(val listType: String)

/**
 * 用户服务记录页面路由
 * @param userId 用户ID
 * @param userName 用户昵称
 * @param userAddress 用户地址
 */
@Keep
@Serializable
data class UserServiceRecordRoute(val userId: Long, val userName: String, val userAddress: String)

/**
 * NFC测试页面路由
 */
@Keep
@Serializable
object NfcTestRoute

/**
 * 相机页面路由
 * @param watermarkData 水印数据
 */
@Keep
@Serializable
data class CameraRoute(val watermarkData: WatermarkData)

/**
 * 手动人脸捕获页面路由
 */
@Serializable
object ManualFaceCaptureRoute
