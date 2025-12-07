package com.ytone.longcare.network.interceptor

import android.content.Context
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.api.response.ServiceOrderStateModel
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor(private val context: Context) : Interceptor {
    
    companion object {
        /** 随机模式标识 */
        private const val RANDOM_MODE = -99
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!BuildConfig.USE_MOCK_DATA) {
            return chain.proceed(chain.request())
        }

        val request = chain.request()
        val path = request.url.encodedPath
        val method = request.method.uppercase()

        val mockJson = findMockData(path, method)

        if (mockJson != null) {
            return Response.Builder()
                .code(200)
                .message("OK (Mocked)")
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .body(mockJson.toResponseBody("application/json".toMediaTypeOrNull()))
                .addHeader("content-type", "application/json")
                .build()
        }

        return chain.proceed(request)
    }

    private fun findMockData(path: String, method: String): String? {
        // CheckEndOrder 接口特殊处理：动态返回成功或3005错误
        if (path == "/V1/Service/CheckEndOrder") {
            // 使用随机数决定返回成功还是3005错误，50%概率返回3005错误
            val mockFile = if (kotlin.random.Random.nextBoolean()) {
                "mock/check_end_order_error_3005.json"
            } else {
                "mock/common_success_unit.json"
            }
            
            return try {
                context.assets.open(mockFile).bufferedReader().use { reader ->
                    reader.readText()
                }
            } catch (e: Exception) {
                // 如果文件读取失败，可以返回一个错误信息的JSON
                """{"resultCode": 500, "resultMsg": "Mock文件读取失败: ${e.message}", "data": null}"""
            }
        }
        
        // OrderState 接口特殊处理：可配置返回不同状态
        // 修改 ORDER_STATE_MOCK_VALUE 的值来测试不同场景，使用 ServiceOrderStateModel 中的常量：
        // STATE_IN_PROGRESS (1) = 执行中（正常状态，不触发弹窗）
        // STATE_COMPLETED (2) = 任务完成（触发异常弹窗）
        // STATE_CANCELLED (3) = 作废（触发异常弹窗）
        // STATE_PENDING (0) = 待执行（触发异常弹窗）
        // STATE_NOT_CREATED (-1) = 未开单（触发异常弹窗）
        // RANDOM_MODE (-99) = 随机返回（用于测试）
        if (path == "/V1/Service/OrderState") {
            // ========== 修改此值来测试不同场景 ==========
            val ORDER_STATE_MOCK_VALUE = RANDOM_MODE
            // ============================================
            
            val mockFile = when (ORDER_STATE_MOCK_VALUE) {
                ServiceOrderStateModel.STATE_IN_PROGRESS -> "mock/order_state_in_progress.json"
                ServiceOrderStateModel.STATE_COMPLETED -> "mock/order_state_completed.json"
                ServiceOrderStateModel.STATE_CANCELLED -> "mock/order_state_cancelled.json"
                ServiceOrderStateModel.STATE_PENDING -> "mock/order_state_pending.json"
                ServiceOrderStateModel.STATE_NOT_CREATED -> "mock/order_state_not_created.json"
                RANDOM_MODE -> {
                    // 随机返回：90%概率返回执行中，10%概率返回异常状态
                    val random = kotlin.random.Random.nextInt(10)
                    when {
                        random < 9 -> "mock/order_state_in_progress.json"
                        else -> "mock/order_state_completed.json"
                    }
                }
                else -> "mock/order_state_in_progress.json"
            }
            
            return try {
                context.assets.open(mockFile).bufferedReader().use { reader ->
                    reader.readText()
                }
            } catch (e: Exception) {
                """{"resultCode": 500, "resultMsg": "Mock文件读取失败: ${e.message}", "data": null}"""
            }
        }
        
        val mockFile = when (path) {
            // 返回 Unit 的通用成功响应
            "/V1/Phone/SendSmsCode",
            "/V1/Login/Log",
            "/V1/Service/StarOrder",  // 修正：StartOrder -> StarOrder
            "/V1/Service/EndOrder",
            "/V1/Service/AddPostion",
            "/V1/Service/CheckOrder",  // 新增：工单前校验
            "/V1/Service/UpUserStartImg",  // 新增：添加开始老人照片
            "/V1/User/SetFace",  // 新增：设置人脸信息
            "/V1/Login/Out" -> "mock/common_success_unit.json"  // 新增：退出登录

            // 具体数据模型的响应
            "/V1/Login/Phone" -> "mock/login_phone.json"
            "/V1/Service/OrderList" -> "mock/order_list.json"
            "/V1/Service/TodayOrder" -> "mock/today_order_list.json"
            "/V1/Service/InOrder" -> "mock/in_order_list.json"
            "/V1/Service/OrderInfo" -> "mock/order_info.json"
            "/V1/Service/Statistics" -> "mock/service_statistics.json"
            "/V1/Service/HaveServiceUserList",
            "/V1/Service/NoServiceUserList" -> "mock/user_info_list.json"
            "/V1/Service/UserOrderList" -> "mock/user_order_list.json"
            "/V1/File/UploadToken" -> "mock/upload_token.json"  // 修正：路径从 /V1/Common/UploadToken 改为 /V1/File/UploadToken
            "/V1/File/GetFileUrl" -> "mock/file_url.json"  // 新增：获取文件访问链接
            "/V1/Common/Config" -> "mock/system_config.json"
            "/V1/System/Config" -> "mock/system_config.json"  // 新增：系统配置接口
            "/V1/System/Start" -> "mock/start_config.json"  // 新增：启动前配置
            "/V1/System/ChecVersion" -> "mock/app_version.json"

            else -> null
        }

        return mockFile?.let {
            try {
                context.assets.open(it).bufferedReader().use { reader ->
                    reader.readText()
                }
            } catch (e: Exception) {
                // 如果文件读取失败，可以返回一个错误信息的JSON
                """{"resultCode": 500, "resultMsg": "Mock文件读取失败: ${e.message}", "data": null}"""
            }
        }
    }
}