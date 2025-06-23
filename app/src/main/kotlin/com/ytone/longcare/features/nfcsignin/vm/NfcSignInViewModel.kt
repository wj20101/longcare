package com.ytone.longcare.features.nfcsignin.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.features.nfcsignin.ui.SignInMode
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
class NfcSignInViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val toastHelper: ToastHelper,
    private val appEventBus: AppEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow<NfcSignInUiState>(NfcSignInUiState.Initial)
    val uiState: StateFlow<NfcSignInUiState> = _uiState.asStateFlow()

    /**
     * 开始服务工单
     * @param orderId 订单ID
     * @param nfcDeviceId NFC设备号
     */
    fun startOrder(orderId: Long, nfcDeviceId: String) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            when (val result = orderRepository.startOrder(orderId, nfcDeviceId)) {
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
     */
    fun endOrder(
        orderId: Long,
        nfcDeviceId: String,
        projectIdList: List<Int>,
        beginImgList: List<String>,
        endImageList: List<String>
    ) {
        viewModelScope.launch {
            _uiState.value = NfcSignInUiState.Loading

            when (val result = orderRepository.endOrder(
                orderId,
                nfcDeviceId,
                projectIdList,
                beginImgList,
                endImageList
            )) {
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
        orderId: Long,
        signInMode: SignInMode,
        endOderInfo: EndOderInfo?
    ) {
        viewModelScope.launch {
            appEventBus.events.collect { event ->
                if (event is AppEvent.NfcIntentReceived) {
                    val tag = NfcUtils.getTagFromIntent(event.intent)
                    if (tag != null) {
                        val tagId = NfcUtils.bytesToHexString(tag.id)
                        if (tagId.isNotEmpty()) {
                            when (signInMode) {
                                SignInMode.START_ORDER -> startOrder(orderId, tagId)

                                SignInMode.END_ORDER -> {
                                    endOderInfo?.let {
                                        endOrder(
                                            orderId = orderId,
                                            nfcDeviceId = tagId,
                                            projectIdList = it.projectIdList,
                                            beginImgList = it.beginImgList,
                                            endImageList = it.endImgList
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