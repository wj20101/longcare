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

            when (val result = orderRepository.startOrder(orderInfoRequest.orderId, nfcDeviceId, longitude, latitude)) {
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
     * @param projectIdList 完成的服务项目ID集合
     * @param beginImgList 开始图片集合
     * @param endImageList 结束图片集合
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
        longitude: String = "",
        latitude: String = "",
        endType: Int = 1
    ) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            when (val result = orderRepository.endOrder(
                orderInfoRequest.orderId,
                nfcDeviceId,
                projectIdList,
                beginImgList,
                endImageList,
                longitude,
                latitude,
                endType
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
        onLocationRequest: suspend () -> Pair<String, String>
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
                                SignInMode.START_ORDER -> startOrder(orderInfoRequest, tagId, longitude, latitude)

                                SignInMode.END_ORDER -> {
                                    endOderInfo?.let {
                                        endOrder(
                                            orderInfoRequest = orderInfoRequest,
                                            nfcDeviceId = tagId,
                                            projectIdList = it.projectIdList,
                                            beginImgList = it.beginImgList,
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