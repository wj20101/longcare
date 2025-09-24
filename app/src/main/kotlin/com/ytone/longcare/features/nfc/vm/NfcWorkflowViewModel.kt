package com.ytone.longcare.features.nfc.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.SelectedProjectsManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.SignInMode
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.OrderDetailUiState
import com.ytone.longcare.api.response.ServiceOrderInfoModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * NFC签到页面的ViewModel
 */
@HiltViewModel
class NfcWorkflowViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val appEventBus: AppEventBus,
    private val selectedProjectsManager: SelectedProjectsManager
) : ViewModel() {

    private val _uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    val uiState: StateFlow<NfcSignInUiState> = _uiState.asStateFlow()

    // 定位激活弹窗状态
    private val _showLocationActivationDialog = MutableStateFlow(false)
    val showLocationActivationDialog: StateFlow<Boolean> = _showLocationActivationDialog.asStateFlow()

    // 当前待处理的NFC数据
    private var pendingNfcData: PendingNfcData? = null

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

            when (val result = orderRepository.checkOrder(orderInfoRequest.orderId, nfcDeviceId, longitude, latitude)) {
                is ApiResult.Success -> {
                    _uiState.value = NfcSignInUiState.Success
                }

                is ApiResult.Exception -> {
                    _uiState.value = NfcSignInUiState.Error(
                        result.exception.message ?: "网络错误，请检查网络连接"
                    )
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
     * @param porjectIdList 完成的服务项目ID集合
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
        porjectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>,
        centerImgList: List<String> = emptyList(),
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            when (val result = orderRepository.endOrder(
                orderId = orderInfoRequest.orderId,
                nfcDeviceId = nfcDeviceId,
                projectIdList = porjectIdList,
                beginImgList = beginImgList,
                centerImgList = centerImgList,
                endImageList = endImageList,
                longitude = longitude,
                latitude = latitude,
                endType = endType
            )) {
                is ApiResult.Success -> {
                    // 订单结束成功后清除本地存储的选中项目数据
                    selectedProjectsManager.clearSelectedProjects(orderInfoRequest.orderId)
                    _uiState.value = NfcSignInUiState.Success
                }

                is ApiResult.Exception -> {
                    _uiState.value = NfcSignInUiState.Error(
                        result.exception.message ?: "网络错误，请检查网络连接"
                    )
                }

                is ApiResult.Failure -> {
                    toastHelper.showShort(result.message)
                    _uiState.value = NfcSignInUiState.Error(result.message)
                }
            }
        }
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
        _uiState.value = NfcSignInUiState.Error(message)
    }

    fun observeNfcEvents(
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        onLocationRequest: suspend () -> Pair<String, String>,
        sharedOrderDetailViewModel: SharedOrderDetailViewModel
    ) {
        viewModelScope.launch {
            appEventBus.events.collect { event ->
                if (event is AppEvent.NfcIntentReceived) {
                    val tag = NfcUtils.getTagFromIntent(event.intent)
                    if (tag != null) {
                        val tagId = NfcUtils.bytesToHexString(tag.id)
                        if (tagId.isNotEmpty()) {
                            // 实时获取位置信息
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
                                        latitude = latitude,
                                        sharedOrderDetailViewModel = sharedOrderDetailViewModel
                                    )
                                }

                                SignInMode.END_ORDER -> {
                                    endOderInfo?.let {
                                        endOrder(
                                            orderInfoRequest = orderInfoRequest,
                                            nfcDeviceId = tagId,
                                            porjectIdList = it.projectIdList,
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
    private fun checkUserLocationAndProceed(
        orderInfoRequest: OrderInfoRequestModel,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?,
        tagId: String,
        longitude: String,
        latitude: String,
        sharedOrderDetailViewModel: SharedOrderDetailViewModel
    ) {
        // 获取订单详情
        val orderInfo = sharedOrderDetailViewModel.getCachedOrderInfo(orderInfoRequest)
        
        if (orderInfo == null) {
            // 如果缓存中没有订单详情，先获取订单详情
            sharedOrderDetailViewModel.getOrderInfo(orderInfoRequest)
            // 启动一个新的协程来监听状态变化
            viewModelScope.launch {
                sharedOrderDetailViewModel.uiState.collect { state ->
                    when (state) {
                        is OrderDetailUiState.Success -> {
                            checkLocationAndShowDialog(
                                orderInfo = state.orderInfo,
                                orderInfoRequest = orderInfoRequest,
                                signInMode = signInMode,
                                endOderInfo = endOderInfo,
                                tagId = tagId,
                                longitude = longitude,
                                latitude = latitude
                            )
                            return@collect // 处理完成后退出collect
                        }
                        is OrderDetailUiState.Error -> {
                            showError("获取订单详情失败: ${state.message}")
                            return@collect // 出错后退出collect
                        }
                        else -> {
                            // Loading或Initial状态，继续等待
                        }
                    }
                }
            }
        } else {
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
            // 用户位置信息为空，显示定位激活弹窗
            pendingNfcData = PendingNfcData(
                orderInfoRequest = orderInfoRequest,
                signInMode = signInMode,
                endOderInfo = endOderInfo,
                tagId = tagId,
                longitude = longitude,
                latitude = latitude
            )
            _showLocationActivationDialog.value = true
        } else {
            // 用户位置信息不为空，直接执行签到
            startOrder(orderInfoRequest, tagId, longitude, latitude)
        }
    }

    /**
     * 确认激活定位
     */
    fun confirmLocationActivation() {
        viewModelScope.launch {
            pendingNfcData?.let { data ->
                _showLocationActivationDialog.value = false
                
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
                
                pendingNfcData = null
            }
        }
    }

    /**
     * 取消定位激活
     */
    fun cancelLocationActivation() {
        _showLocationActivationDialog.value = false
        pendingNfcData = null
    }
}

/**
 * NFC签到页面的UI状态密封类
 */
sealed class NfcSignInUiState {
    data object Loading : NfcSignInUiState()
    data object Success : NfcSignInUiState()
    data class Error(val message: String) : NfcSignInUiState()
    data object Initial : NfcSignInUiState()
}