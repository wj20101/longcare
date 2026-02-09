package com.ytone.longcare.navigation

import androidx.annotation.Keep
import com.ytone.longcare.features.photoupload.model.WatermarkData
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
 * NFC签到模式枚举
 */
@Keep
@Serializable
enum class SignInMode {
    /** 开始订单模式 */
    START_ORDER,
    /** 结束订单模式 */
    END_ORDER
}

/**
 * NFC签到路由
 * @param orderParams 订单导航参数
 * @param signInMode 签到模式（开始订单或结束订单）
 * @param endOrderParams 结束订单时的参数信息
 */
@Keep
@Serializable
data class NfcSignInRoute(
    val orderParams: OrderNavParams,
    val signInMode: SignInMode,
    val endOrderParams: EndOderInfo? = null
)

/**
 * 服务倒计时页面路由
 * @param orderParams 订单导航参数
 * @param projectIdList 选中的项目ID列表
 */
@Keep
@Serializable
data class ServiceCountdownRoute(val orderParams: OrderNavParams, val projectIdList: List<Int> = emptyList())

/**
 * 结束订单信息数据类
 * @param projectIdList 项目ID列表
 * @param beginImgList 开始时的图片列表
 * @param centerImgList 服务中的图片列表
 * @param endImgList 结束时的图片列表
 * @param endType 结束类型：1=正常结束，2=提前结束
 */
@Keep
@Serializable
data class EndOderInfo(
    val projectIdList: List<Int> = emptyList(),
    val beginImgList: List<String> = emptyList(),
    val centerImgList: List<String> = emptyList(),
    val endImgList: List<String> = emptyList(),
    val endType: Int = 1
)

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
 * 服务完成页面路由
 * @param orderParams 订单导航参数
 * @param serviceCompleteData 服务完成数据
 */
@Keep
@Serializable
data class ServiceCompleteRoute(
    val orderParams: OrderNavParams,
    val serviceCompleteData: ServiceCompleteData
)

/**
 * 服务完成页面所需的数据
 * @param clientName 客户姓名
 * @param clientAge 客户年龄
 * @param clientIdNumber 客户身份证号
 * @param clientAddress 客户地址
 * @param serviceContent 服务内容（已计算好的字符串）
 * @param trueServiceTime 实际服务时长（分钟）
 */
@Keep
@Serializable
data class ServiceCompleteData(
    val clientName: String = "",
    val clientAge: Int = 0,
    val clientIdNumber: String = "",
    val clientAddress: String = "",
    val serviceContent: String = "",
    val trueServiceTime: Int = 0
)

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

/**
 * 结束服务选择页面路由
 * @param orderParams 订单导航参数
 * @param endType 结束类型
 * @param initialProjectIdList 初始项目ID列表
 */
@Keep
@Serializable
data class EndServiceSelectionRoute(
    val orderParams: OrderNavParams,
    val endType: Int,
    val initialProjectIdList: List<Int> = emptyList()
)
