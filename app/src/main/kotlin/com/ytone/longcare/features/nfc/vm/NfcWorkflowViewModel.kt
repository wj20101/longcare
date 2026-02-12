package com.ytone.longcare.features.nfc.vm


import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.common.utils.logE
import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.features.location.core.LocationFacade
import com.ytone.longcare.navigation.ServiceCompleteData
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NFC签到页面的ViewModel
 */
@HiltViewModel
class NfcWorkflowViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val appEventBus: AppEventBus,
    private val nfcManager: NfcManager,
    private val locationFacade: LocationFacade,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val countdownNotificationManager: CountdownNotificationManager
) : ViewModel() {
    private var nfcEventJob: Job? = null

    private val _uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    val uiState: StateFlow<NfcSignInUiState> = _uiState.asStateFlow()

    // 当前待处理的NFC数据, 当这个值不为空时，UI层应该显示一个对话框
    private val _pendingNfcData = MutableStateFlow<PendingNfcData?>(null)
    val pendingNfcData: StateFlow<PendingNfcData?> = _pendingNfcData.asStateFlow()

    data class PendingNfcData(
        val orderInfoRequest: OrderInfoRequestModel,
        val signInMode: SignInMode,
        val endOderInfo: EndOderInfo?,
        val tagId: String,
        val longitude: String,
        val latitude: String
    )

    /**
     * 开始服务工单
     * @param orderInfoRequest 订单信息
     * @param nfcDeviceId NFC设备号
     * @param longitude 经度
     * @param latitude 纬度
     */
    fun startOrder(
        orderInfoRequest: OrderInfoRequestModel,
        nfcDeviceId: String,
        longitude: String = "",
        latitude: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            when (val result = orderRepository.checkOrder(
                orderInfoRequest.orderId,
                nfcDeviceId,
                longitude,
                latitude
            )) {
                is ApiResult.Success -> {
                    _uiState.value = NfcSignInUiState.Success()
                }

                is ApiResult.Exception -> {
                    val message = result.exception.message ?: "网络错误，请检查网络连接"
                    toastHelper.showShort(message)
                    _uiState.value = NfcSignInUiState.Error(message)
                }

                is ApiResult.Failure -> {
                    toastHelper.showShort(result.message)
                    _uiState.value = NfcSignInUiState.Error(result.message)
                }
            }
        }
    }

    /**
     * 结束服务工单
     * @param orderInfoRequest 订单信息
     * @param nfcDeviceId NFC设备号
     * @param projectIdList 完成的服务项目ID集合
     * @param beginImgList 开始图片集合
     * @param endImageList 结束图片集合
     * @param centerImgList 服务中图片集合
     * @param longitude 经度
     * @param latitude 纬度
     * @param endType 结束类型：1=正常结束，2=提前结束
     */
    fun endOrder(
        orderInfoRequest: OrderInfoRequestModel,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            // 先调用 checkEndOrder
            when (val checkResult = orderRepository.checkEndOrder(
                orderId = orderInfoRequest.orderId,
                projectIdList = projectIdList
            )) {
                is ApiResult.Success -> {
                    // checkEndOrder 成功，直接调用 endOrder
                    executeEndOrder(
                        orderInfoRequest = orderInfoRequest,
                        nfcDeviceId = nfcDeviceId,
                        projectIdList = projectIdList,
                        beginImgList = beginImgList,
                        endImageList = endImageList,
                        centerImgList = centerImgList,
                        longitude = longitude,
                        latitude = latitude,
                        endType = endType
                    )
                }

                is ApiResult.Exception -> {
                    val message = checkResult.exception.message ?: "网络错误，请检查网络连接"
                    toastHelper.showShort(message)
                    _uiState.value = NfcSignInUiState.Error(message)
                }

                is ApiResult.Failure -> {
                    // 检查是否是状态码 3005
                    if (checkResult.code == 3005) {
                        // 显示确认对话框
                        _uiState.value = NfcSignInUiState.ShowConfirmDialog(
                            message = checkResult.message,
                            endOrderParams = EndOrderParams(
                                orderInfoRequest = orderInfoRequest,
                                nfcDeviceId = nfcDeviceId,
                                porjectIdList = projectIdList,
                                beginImgList = beginImgList,
                                endImageList = endImageList,
                                centerImgList = centerImgList,
                                longitude = longitude,
                                latitude = latitude,
                                endType = endType
                            )
                        )
                    } else {
                        toastHelper.showShort(checkResult.message)
                        _uiState.value = NfcSignInUiState.Error(checkResult.message)
                    }
                }
            }
        }
    }

    /**
     * 执行结束订单操作
     */
    private suspend fun executeEndOrder(
        orderInfoRequest: OrderInfoRequestModel,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String>,
        longitude: String,
        latitude: String,
        endType: Int
    ) {
        logI("executeEndOrder: Begin: ${beginImgList.size}, Center: ${centerImgList.size}, End: ${endImageList.size}", tag = "NfcWorkflowViewModel")
        
        when (val result = orderRepository.endOrder(
            orderId = orderInfoRequest.orderId,
            nfcDeviceId = nfcDeviceId,
            projectIdList = projectIdList,
            beginImgList = beginImgList,
            centerImgList = centerImgList,
            endImageList = endImageList,
            longitude = longitude,
            latitude = latitude,
            endType = endType
        )) {
            is ApiResult.Success -> {
                // 执行资源清理逻辑
                cleanupResources(orderInfoRequest)

                _uiState.value = NfcSignInUiState.Success(
                    endOrderSuccessData = EndOrderSuccessData(
                        trueServiceTime = result.data.trueServiceTime
                    )
                )
            }

            is ApiResult.Exception -> {
                val message = result.exception.message ?: "网络错误，请检查网络连接"
                toastHelper.showShort(message)
                _uiState.value = NfcSignInUiState.Error(message)
            }

            is ApiResult.Failure -> {
                toastHelper.showShort(result.message)
                _uiState.value = NfcSignInUiState.Error(result.message)
            }
        }
    }

    /**
     * 确认结束订单（用户点击确认按钮后调用）
     */
    fun confirmEndOrder(params: EndOrderParams) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading
            executeEndOrder(
                orderInfoRequest = params.orderInfoRequest,
                nfcDeviceId = params.nfcDeviceId,
                projectIdList = params.porjectIdList,
                beginImgList = params.beginImgList,
                endImageList = params.endImageList,
                centerImgList = params.centerImgList,
                longitude = params.longitude,
                latitude = params.latitude,
                endType = params.endType
            )
        }
    }

    /**
     * 取消结束订单（用户点击取消按钮后调用）
     */
    fun cancelEndOrder() {
        _uiState.value = NfcSignInUiState.Initial
    }

    /**
     * 重置状态
     */
    fun resetState() {
        _uiState.value = NfcSignInUiState.Initial
    }

    /**
     * 显示错误信息
     * @param message 错误信息
     */
    fun showError(message: String) {
        toastHelper.showShort(message)
        _uiState.value = NfcSignInUiState.Error(message)
    }

    fun buildServiceCompleteDataFromCache(
        orderInfoRequest: OrderInfoRequestModel,
        endOderInfo: EndOderInfo?,
        trueServiceTime: Int
    ): ServiceCompleteData {
        val cachedOrderInfo = unifiedOrderRepository.getCachedOrderInfo(orderInfoRequest.toOrderKey())
        val userInfo = cachedOrderInfo?.userInfo
        val projectList = cachedOrderInfo?.projectList ?: emptyList()
        val selectedProjectIds = endOderInfo?.projectIdList ?: emptyList()
        val serviceContent = if (selectedProjectIds.isNotEmpty()) {
            projectList
                .filter { selectedProjectIds.contains(it.projectId) }
                .joinToString(", ") { it.projectName }
        } else {
            projectList.joinToString(", ") { it.projectName }
        }

        return ServiceCompleteData(
            clientName = userInfo?.name ?: "",
            clientAge = userInfo?.age ?: 0,
            clientIdNumber = userInfo?.identityCardNumber ?: "",
            clientAddress = userInfo?.address ?: "",
            serviceContent = serviceContent,
            trueServiceTime = trueServiceTime
        )
    }

    fun isNfcSupported(): Boolean {
        return NfcUtils.isNfcSupported(context)
    }

    fun enableNfcForActivity(activity: Activity) {
        nfcManager.enableNfcForActivity(activity)
    }

    fun disableNfcForActivity(activity: Activity) {
        nfcManager.disableNfcForActivity(activity)
    }

    suspend fun getCurrentLocationCoordinates(): Pair<String, String> {
        return try {
            val location = locationFacade.getCurrentLocation()
            if (location != null) {
                Pair(location.longitude.toString(), location.latitude.toString())
            } else {
                Pair("", "")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Pair("", "")
        }
    }

    fun observeNfcEvents(
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> Pair<String, String>
    ) {
        nfcEventJob?.cancel()
        nfcEventJob = viewModelScope.launch {
            appEventBus.events.collect { event ->
                if (event is AppEvent.NfcIntentReceived) {
                    // 如果已经签到/签退成功，忽略后续的NFC事件
                    if (_uiState.value is NfcSignInUiState.Success) {
                        return@collect
                    }

                    val tag = NfcUtils.getTagFromIntent(event.intent)
                    if (tag != null) {
                        val tagId = NfcUtils.bytesToHexString(tag.id)
                        if (tagId.isNotEmpty()) {
                            // 通过定位门面获取位置（缓存优先，失败自动回退）
                            val (longitude, latitude) = onLocationRequest()

                            // 如果定位信息为空，则中断流程，因为参数无效
                            if (longitude.isEmpty() || latitude.isEmpty()) {
                                showError("无法获取位置信息，请检查定位权限和服务")
                                return@collect
                            }

                            when (signInMode) {
                                SignInMode.START_ORDER -> {
                                    // 检查用户位置信息
                                    checkUserLocationAndProceed(
                                        orderInfoRequest = orderInfoRequest,
                                        signInMode = signInMode,
                                        endOderInfo = endOderInfo,
                                        tagId = tagId,
                                        longitude = longitude,
                                        latitude = latitude
                                    )
                                }

                                SignInMode.END_ORDER -> {
                                    endOderInfo?.let {
                                        endOrder(
                                            orderInfoRequest = orderInfoRequest,
                                            nfcDeviceId = tagId,
                                            projectIdList = it.projectIdList,
                                            beginImgList = it.beginImgList,
                                            centerImgList = it.centerImgList,
                                            endImageList = it.endImgList,
                                            longitude = longitude,
                                            latitude = latitude,
                                            endType = it.endType
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 检查用户位置信息并决定是否需要弹出定位激活弹窗
     */
    private suspend fun checkUserLocationAndProceed(
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        tagId: String,
        longitude: String,
        latitude: String
    ) {
        // 获取订单详情
        val orderInfo = unifiedOrderRepository.getCachedOrderInfo(orderInfoRequest.toOrderKey())
            ?: when (val result = unifiedOrderRepository.getOrderInfo(orderInfoRequest.toOrderKey())) {
                is ApiResult.Success -> result.data
                is ApiResult.Exception -> {
                    showError("获取订单详情失败: ${result.exception.message ?: "网络异常"}")
                    return
                }
                is ApiResult.Failure -> {
                    showError("获取订单详情失败: ${result.message}")
                    return
                }
            }

        checkLocationAndShowDialog(
            orderInfo = orderInfo,
            orderInfoRequest = orderInfoRequest,
            signInMode = signInMode,
            endOderInfo = endOderInfo,
            tagId = tagId,
            longitude = longitude,
            latitude = latitude
        )
    }

    /**
     * 检查位置信息并显示弹窗或直接执行
     */
    private fun checkLocationAndShowDialog(
        orderInfo: ServiceOrderInfoModel,
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        tagId: String,
        longitude: String,
        latitude: String
    ) {
        val userLng = orderInfo.userInfo?.lng ?: ""
        val userLat = orderInfo.userInfo?.lat ?: ""

        if (userLng.isEmpty() || userLat.isEmpty()) {
            // 用户位置信息为空，更新待处理数据以显示弹窗
            _pendingNfcData.value = PendingNfcData(
                orderInfoRequest = orderInfoRequest,
                signInMode = signInMode,
                endOderInfo = endOderInfo,
                tagId = tagId,
                longitude = longitude,
                latitude = latitude
            )
        } else {
            // 用户位置信息不为空，直接执行签到
            startOrder(orderInfoRequest, tagId, longitude, latitude)
        }
    }

    /**
     * 确认激活定位
     */
    fun confirmLocationActivation(data: PendingNfcData) {
        viewModelScope.launch {
            // 调用绑定定位接口
            when (val result = orderRepository.bindLocation(
                orderId = data.orderInfoRequest.orderId,
                nfc = data.tagId,
                longitude = data.longitude,
                latitude = data.latitude
            )) {
                is ApiResult.Success -> {
                    // 绑定成功后执行签到
                    startOrder(data.orderInfoRequest, data.tagId, data.longitude, data.latitude)
                }

                is ApiResult.Exception -> {
                    showError(result.exception.message ?: "绑定定位失败")
                }

                is ApiResult.Failure -> {
                    showError(result.message)
                }
            }
            // 清空待处理数据，这将自动隐藏对话框
            _pendingNfcData.value = null
        }
    }

    /**
     * 取消定位激活
     */
    fun cancelLocationActivation() {
        // 清空待处理数据，这将自动隐藏对话框
        _pendingNfcData.value = null
    }

    /**
     * 模拟NFC扫描 (Mock模式)
     */
    fun mockNfcScan(
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?
    ) {
        val mockTagId = "MOCK_TAG_ID_123456"
        val mockLongitude = "121.4737" // 上海坐标
        val mockLatitude = "31.2304"

        viewModelScope.launch {
            when (signInMode) {
                SignInMode.START_ORDER -> {
                    checkUserLocationAndProceed(
                        orderInfoRequest = orderInfoRequest,
                        signInMode = signInMode,
                        endOderInfo = endOderInfo,
                        tagId = mockTagId,
                        longitude = mockLongitude,
                        latitude = mockLatitude
                    )
                }

                SignInMode.END_ORDER -> {
                    endOderInfo?.let {
                        endOrder(
                            orderInfoRequest = orderInfoRequest,
                            nfcDeviceId = mockTagId,
                            projectIdList = it.projectIdList,
                            beginImgList = it.beginImgList,
                            centerImgList = it.centerImgList,
                            endImageList = it.endImgList,
                            longitude = mockLongitude,
                            latitude = mockLatitude,
                            endType = it.endType
                        )
                    }
                }
            }
        }
    }

    /**
     * 清理服务相关资源
     */
    private fun cleanupResources(orderInfoRequest: OrderInfoRequestModel) {
        try {
            // 停止前台服务
            CountdownForegroundService.stopCountdown(context)
            // 停止响铃
            AlarmRingtoneService.stopRingtone(context)
            // 取消倒计时闹钟
            countdownNotificationManager.cancelCountdownAlarmForOrder(orderInfoRequest)

            // 清除本地状态和图片数据
            viewModelScope.launch {
                unifiedOrderRepository.endLocalService(orderInfoRequest.toOrderKey())
                imageRepository.deleteImagesByOrderId(orderInfoRequest.toOrderKey())
            }
        } catch (e: Exception) {
            logE("清理服务相关资源失败: ${e.message}", tag = "NfcWorkflowViewModel", throwable = e)
        }
    }

    override fun onCleared() {
        nfcEventJob?.cancel()
        super.onCleared()
    }
}

/**
 * NFC签到页面的UI状态密封类
 */
sealed class NfcSignInUiState {
    data object Loading : NfcSignInUiState()
    data class Success(
        val endOrderSuccessData: EndOrderSuccessData? = null
    ) : NfcSignInUiState()

    data class Error(val message: String) : NfcSignInUiState()
    data object Initial : NfcSignInUiState()
    data class ShowConfirmDialog(
        val message: String,
        val endOrderParams: EndOrderParams
    ) : NfcSignInUiState()
}

/**
 * 结束订单成功后的数据
 */
data class EndOrderSuccessData(
    val trueServiceTime: Int
)

data class EndOrderParams(
    val orderInfoRequest: OrderInfoRequestModel,
    val nfcDeviceId: String,
    val porjectIdList: List<Int>,
    val beginImgList: List<String>,
    val endImageList: List<String>,
    val centerImgList: List<String>,
    val longitude: String,
    val latitude: String,
    val endType: Int
)
