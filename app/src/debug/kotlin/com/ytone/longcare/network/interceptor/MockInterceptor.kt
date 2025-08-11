package com.ytone.longcare.network.interceptor

import android.content.Context
import com.ytone.longcare.BuildConfig
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class MockInterceptor(private val context: Context) : Interceptor {

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
        val mockFile = when (path) {
            // 返回 Unit 的通用成功响应
            "/V1/Phone/SendSmsCode",
            "/V1/Login/Log",
            "/V1/Service/StarOrder",  // 修正：StartOrder -> StarOrder
            "/V1/Service/EndOrder",
            "/V1/Service/AddPostion",
            "/V1/Service/CheckOrder",  // 新增：工单前校验
            "/V1/Service/UpUserStartImg",  // 新增：添加开始老人照片
            "/V1/Login/Out" -> "mock/common_success_unit.json"  // 新增：退出登录

            // 具体数据模型的响应
            "/V1/Login/Phone" -> "mock/login_phone.json"
            "/V1/Service/OrderList" -> "mock/order_list.json"
            "/V1/Service/TodayOrder" -> "mock/today_order_list.json"
            "/V1/Service/InOrder" -> "mock/in_order_list.json"
            "/V1/Service/OrderInfo" -> "mock/order_info.json"
            "/V1/Service/Statistics" -> "mock/service_statistics.json"
            "/V1/Service/HaveServiceUserList",
            "/V1/Service/NoServiceUserList",
            "/V1/Service/UserOrderList" -> "mock/user_info_list.json"
            "/V1/File/UploadToken" -> "mock/upload_token.json"  // 修正：路径从 /V1/Common/UploadToken 改为 /V1/File/UploadToken
            "/V1/File/GetFileUrl" -> "mock/file_url.json"  // 新增：获取文件访问链接
            "/V1/Common/Config" -> "mock/system_config.json"
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