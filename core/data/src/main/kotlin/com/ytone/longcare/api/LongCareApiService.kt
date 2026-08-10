package com.ytone.longcare.api

import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.network.SuppressSessionInvalidation
import com.ytone.longcare.model.OrderListParamModel
import com.ytone.longcare.model.LoginLogParamModel
import com.ytone.longcare.model.LoginPhoneParamModel
import com.ytone.longcare.model.EndOrderParamModel
import com.ytone.longcare.model.OrderInfoParamModel
import com.ytone.longcare.model.AddPositionParamModel
import com.ytone.longcare.model.UserOrderParamModel
import com.ytone.longcare.model.UploadTokenParamModel
import com.ytone.longcare.model.SaveFileParamModel
import com.ytone.longcare.model.CheckOrderParamModel
import com.ytone.longcare.model.StarOrderParamModel
import com.ytone.longcare.model.UpUserStartImgParamModel
import com.ytone.longcare.model.BindLocationParamModel
import com.ytone.longcare.model.CheckFaceParamModel
import com.ytone.longcare.model.SetFaceParamModel
import com.ytone.longcare.model.FaceResultModel
import com.ytone.longcare.model.CheckEndOrderParamModel
import com.ytone.longcare.model.OrderStateParamModel
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.model.UserOrderModel
import com.ytone.longcare.model.UploadTokenResultModel
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.model.StartConfigResultModel
import com.ytone.longcare.model.EndOrderResultModel
import com.ytone.longcare.model.SendSmsCodeParamModel
import com.ytone.longcare.api.model.AddUserLatentRequestDto
import com.ytone.longcare.api.model.AddUserLatentResponseDto
import com.ytone.longcare.api.model.CheckTokenDto
import com.ytone.longcare.api.model.GetCheckTokenRequestDto
import com.ytone.longcare.api.model.SearchUserLatentRequestDto
import com.ytone.longcare.api.model.ToDoCountDto
import com.ytone.longcare.api.model.ToDoItemDto
import com.ytone.longcare.api.model.UserLatentDetailDto
import com.ytone.longcare.api.model.UserLatentListDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface LongCareApiService {

    /**
     * 启动前的配置信息
     * 获取用户协议和隐私政策的URL
     *
     * @return 启动配置信息
     */
    @GET("/V1/System/Start")
    suspend fun getStartConfig(): ApiResult<StartConfigResultModel>

    /**
     * 发送短信验证码
     *
     * @param sendSmsCodeParamModel 请求参数
     * @return 无返回值
     */
    @POST("/V1/Phone/SendSmsCode")
    suspend fun sendSmsCode(@Body sendSmsCodeParamModel: SendSmsCodeParamModel): ApiResult<Unit>

    /**
     * 手机号码登录
     *
     * @param loginPhoneParamModel 登录参数
     * @return 登录结果
     */
    @POST("/V1/Login/Phone")
    suspend fun phoneLogin(@Body loginPhoneParamModel: LoginPhoneParamModel): ApiResult<LoginResultModel>

    /**
     * 按天查询服务订单
     *
     * @param orderListParamModel 包含查询日期的请求体
     * @return 返回服务订单列表
     */
    @POST("/V1/Service/OrderList")
    suspend fun getOrderList(@Body orderListParamModel: OrderListParamModel): ApiResult<List<ServiceOrderModel>>

    /**
     * 记录登录日志
     *
     * @param loginLogParamModel 登录日志参数
     * @return 无返回值
     */
    @POST("/V1/Login/Log")
    @SuppressSessionInvalidation
    suspend fun recordLoginLog(@Body loginLogParamModel: LoginLogParamModel): ApiResult<Unit>

    /**
     * 获取今天的服务订单
     *
     * @return 返回今天的服务订单列表
     */
    @GET("/V1/Service/TodayOrder")
    suspend fun getTodayOrderList(): ApiResult<List<TodayServiceOrderModel>>

    /**
     * 查询服务中的订单
     *
     * @return 返回服务中的订单列表
     */
    @GET("/V1/Service/InOrder")
    suspend fun getInOrderList(): ApiResult<List<ServiceOrderModel>>

    /**
     * 查询服务订单详情
     *
     * @param orderInfoParamModel 请求参数，包含订单号
     * @return 返回服务订单详情
     */
    @POST("/V1/Service/OrderInfo")
    suspend fun getOrderInfo(@Body orderInfoParamModel: OrderInfoParamModel): ApiResult<ServiceOrderInfoModel>

    /**
     * 工单开始(正式计时)
     *
     * @param starOrderParamModel 请求参数，包含订单ID
     * @return 无返回值
     */
    @POST("/V1/Service/StarOrder")
    suspend fun starOrder(@Body starOrderParamModel: StarOrderParamModel): ApiResult<Unit>

    /**
     * 结束服务工单
     *
     * @param endOrderParamModel 请求参数
     * @return 无返回值
     */
    @POST("/V1/Service/EndOrder")
    suspend fun endOrder(@Body endOrderParamModel: EndOrderParamModel): ApiResult<EndOrderResultModel>

    /**
     * 添加定位
     */
    @POST("/V1/Service/AddPostion")
    suspend fun addPosition(@Body param: AddPositionParamModel): ApiResult<Unit>

    /**
     * 获取本月服务统计信息
     */
    @GET("/V1/Service/Statistics")
    suspend fun getServiceStatistics(): ApiResult<NurseServiceTimeModel>

    /**
     * 获取本月已服务的用户列表
     */
    @GET("/V1/Service/HaveServiceUserList")
    suspend fun getHaveServiceUserList(): ApiResult<List<UserInfoModel>>

    /**
     * 获取本月未服务的用户列表
     */
    @GET("/V1/Service/NoServiceUserList")
    suspend fun getNoServiceUserList(): ApiResult<List<UserInfoModel>>

    /**
     * 获取本月用户的服务记录情况
     */
    @POST("/V1/Service/UserOrderList")
    suspend fun getUserOrderList(@Body param: UserOrderParamModel): ApiResult<List<UserOrderModel>>

    /**
     * 获取文件上传token
     */
    @POST("/V1/File/UploadToken")
    suspend fun getUploadToken(@Body uploadTokenParamModel: UploadTokenParamModel): ApiResult<UploadTokenResultModel>

    /**
     * 文件上传完之后获取访问连接,因为图片是私有的
     */
    @POST("/V1/File/GetFileUrl")
    suspend fun getFileUrl(@Body saveFileParamModel: SaveFileParamModel): ApiResult<String>

    /**
     * 系统相关配置
     */
    @GET("/V1/System/Config")
    suspend fun getSystemConfig(): ApiResult<SystemConfigModel>

    /**
     * 退出登录
     */
    @GET("/V1/Login/Out")
    suspend fun logout(): ApiResult<Unit>

    /**
     * 工单前校验
     *
     * @param checkOrderParamModel 请求参数，包含订单ID、NFC设备号和位置信息
     * @return 无返回值
     */
    @POST("/V1/Service/CheckOrder")
    suspend fun checkOrder(@Body checkOrderParamModel: CheckOrderParamModel): ApiResult<Unit>

    /**
     * 添加开始老人照片
     *
     * @param upUserStartImgParamModel 请求参数，包含订单ID和用户图片集合
     * @return 无返回值
     */
    @POST("/V1/Service/UpUserStartImg")
    suspend fun upUserStartImg(@Body upUserStartImgParamModel: UpUserStartImgParamModel): ApiResult<Unit>

    /**
     * 版本检测
     */
    @GET("/V1/System/ChecVersion")
    suspend fun checkVersion(): ApiResult<AppVersionModel>

    /**
     * 绑定定位
     *
     * @param bindLocationParamModel 请求参数
     * @return 无返回值
     */
    @POST("/V1/Service/BindLocation")
    suspend fun bindLocation(@Body bindLocationParamModel: BindLocationParamModel): ApiResult<Unit>

    /**
     * 设置人脸信息
     *
     * @param setFaceParamModel 请求参数，包含人脸图片信息
     * @return 无返回值
     */
    @POST("/V1/User/SetFace")
    suspend fun setFace(@Body setFaceParamModel: SetFaceParamModel): ApiResult<Unit>

    /**
     * 获取人脸信息
     *
     * @return FaceResultModel，包含人脸图片地址
     */
    @GET("/V1/User/GetFace")
    suspend fun getFace(): ApiResult<FaceResultModel>

    /**
     * 对订单关联的人脸图片进行对比验证。
     *
     * @param checkFaceParamModel 订单 ID 与 Base64 人脸图片
     * @return 无返回值
     */
    @POST("/V1/User/CheckFace")
    suspend fun checkFace(@Body checkFaceParamModel: CheckFaceParamModel): ApiResult<Unit>

    /**
     * 检测结束工单
     *
     * @param checkEndOrderParamModel 请求参数，包含订单ID和完成的服务项目ID集合
     * @return 无返回值
     */
    @POST("/V1/Service/CheckEndOrder")
    suspend fun checkEndOrder(@Body checkEndOrderParamModel: CheckEndOrderParamModel): ApiResult<Unit>

    /**
     * 查询服务订单状态
     *
     * @param orderStateParamModel 请求参数，包含订单ID
     * @return 订单状态信息
     */
    @POST("/V1/Service/OrderState")
    suspend fun getOrderState(@Body orderStateParamModel: OrderStateParamModel): ApiResult<ServiceOrderStateModel>

    /**
     * 获取一次性俏郎中检测 Token。
     */
    @POST("/V1/Sale/GetCheckToken")
    suspend fun getCheckToken(
        @Body request: GetCheckTokenRequestDto,
    ): ApiResult<CheckTokenDto>

    /**
     * 添加潜在客户。
     */
    @POST("/V1/Sale/AddUserLatent")
    suspend fun addUserLatent(
        @Body request: AddUserLatentRequestDto,
    ): ApiResult<AddUserLatentResponseDto>

    /**
     * 查询最近 10 个潜在客户。
     */
    @GET("/V1/Sale/GetRecentUserLatentList")
    suspend fun getRecentUserLatentList(): ApiResult<List<UserLatentListDto>>

    /**
     * 查询当前账号的待办事项数量。
     */
    @GET("/V1/Sale/ToDoNum")
    suspend fun getToDoCount(): ApiResult<ToDoCountDto>

    /**
     * 查询当前账号的待办事项列表。
     */
    @GET("/V1/Sale/ToDoList")
    suspend fun getToDoList(): ApiResult<List<ToDoItemDto>>

    /**
     * 按姓名和审核状态搜索潜在客户。
     */
    @POST("/V1/Sale/SearchUserLatentList")
    suspend fun searchUserLatentList(
        @Body request: SearchUserLatentRequestDto,
    ): ApiResult<List<UserLatentListDto>>

    /**
     * 查询潜在客户详情。
     */
    @GET("/V1/Sale/GetUserLatentDetail")
    suspend fun getUserLatentDetail(
        @Query("id") customerId: Int,
    ): ApiResult<UserLatentDetailDto>
}
