package com.ytone.longcare.api

import com.ytone.longcare.api.request.OrderListParamModel
import com.ytone.longcare.api.request.LoginLogParamModel
import com.ytone.longcare.api.request.LoginPhoneParamModel
import com.ytone.longcare.api.request.EndOrderParamModel
import com.ytone.longcare.api.request.OrderInfoParamModel
import com.ytone.longcare.api.request.StartOrderParamModel
import com.ytone.longcare.api.request.AddPositionParamModel
import com.ytone.longcare.api.request.UserOrderParamModel
import com.ytone.longcare.api.request.UploadTokenParamModel
import com.ytone.longcare.api.request.SaveFileParamModel
import com.ytone.longcare.api.response.LoginResultModel
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.api.response.ServiceOrderModel
import com.ytone.longcare.api.response.TodayServiceOrderModel
import com.ytone.longcare.api.response.NurseServiceTimeModel
import com.ytone.longcare.api.response.UserInfoModel
import com.ytone.longcare.api.response.UploadTokenResultModel
import com.ytone.longcare.api.response.SystemConfigModel
import com.ytone.longcare.api.request.SendSmsCodeParamModel
import com.ytone.longcare.model.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LongCareApiService {

    /**
     * 发送短信验证码
     *
     * @param sendSmsCodeParamModel 请求参数
     * @return 无返回值
     */
    @POST("/V1/Phone/SendSmsCode")
    suspend fun sendSmsCode(@Body sendSmsCodeParamModel: SendSmsCodeParamModel): Response<Unit>

    /**
     * 手机号码登录
     *
     * @param loginPhoneParamModel 登录参数
     * @return 登录结果
     */
    @POST("/V1/Login/Phone")
    suspend fun phoneLogin(@Body loginPhoneParamModel: LoginPhoneParamModel): Response<LoginResultModel>

    /**
     * 按天查询服务订单
     *
     * @param orderListParamModel 包含查询日期的请求体
     * @return 返回服务订单列表
     */
    @POST("/V1/Service/OrderList")
    suspend fun getOrderList(@Body orderListParamModel: OrderListParamModel): Response<List<ServiceOrderModel>>

    /**
     * 记录登录日志
     *
     * @param loginLogParamModel 登录日志参数
     * @return 无返回值
     */
    @POST("/V1/Login/Log")
    suspend fun recordLoginLog(@Body loginLogParamModel: LoginLogParamModel): Response<Unit>

    /**
     * 获取今天的服务订单
     *
     * @return 返回今天的服务订单列表
     */
    @GET("/V1/Service/TodayOrder")
    suspend fun getTodayOrderList(): Response<List<TodayServiceOrderModel>>

    /**
     * 查询服务订单详情
     *
     * @param orderInfoParamModel 请求参数，包含订单号
     * @return 返回服务订单详情
     */
    @POST("/V1/Service/OrderInfo")
    suspend fun getOrderInfo(@Body orderInfoParamModel: OrderInfoParamModel): Response<ServiceOrderInfoModel>

    /**
     * 开始服务工单
     *
     * @param startOrderParamModel 请求参数，包含订单ID和NFC设备号
     * @return 无返回值
     */
    @POST("/V1/Service/StartOrder")
    suspend fun startOrder(@Body startOrderParamModel: StartOrderParamModel): Response<Unit>

    /**
     * 结束服务工单
     *
     * @param endOrderParamModel 请求参数
     * @return 无返回值
     */
    @POST("/V1/Service/EndOrder")
    suspend fun endOrder(@Body endOrderParamModel: EndOrderParamModel): Response<Unit>

    /**
     * 添加定位
     */
    @POST("/V1/Service/AddPostion")
    suspend fun addPosition(@Body param: AddPositionParamModel): Response<Unit>

    /**
     * 获取本月服务统计信息
     */
    @GET("/V1/Service/Statistics")
    suspend fun getServiceStatistics(): Response<NurseServiceTimeModel>

    /**
     * 获取本月已服务的用户列表
     */
    @GET("/V1/Service/HaveServiceUserList")
    suspend fun getHaveServiceUserList(): Response<List<UserInfoModel>>

    /**
     * 获取本月未服务的用户列表
     */
    @GET("/V1/Service/NoServiceUserList")
    suspend fun getNoServiceUserList(): Response<List<UserInfoModel>>

    /**
     * 获取本月用户的服务记录情况
     */
    @POST("/V1/Service/UserOrderList")
    suspend fun getUserOrderList(@Body param: UserOrderParamModel): Response<List<UserInfoModel>>

    /**
     * 获取文件上传token
     */
    @POST("/V1/File/UploadToken")
    suspend fun getUploadToken(@Body uploadTokenParamModel: UploadTokenParamModel): Response<UploadTokenResultModel>

    /**
     * 文件上传完之后获取访问连接,因为图片是私有的
     */
    @POST("/V1/File/GetFileUrl")
    suspend fun getFileUrl(@Body saveFileParamModel: SaveFileParamModel): Response<String>

    /**
     * 系统相关配置
     */
    @GET("/V1/Common/Config")
    suspend fun getSystemConfig(): Response<SystemConfigModel>

    /**
     * 退出登录
     */
    @GET("/V1/Login/Out")
    suspend fun logout(): Response<Unit>

}