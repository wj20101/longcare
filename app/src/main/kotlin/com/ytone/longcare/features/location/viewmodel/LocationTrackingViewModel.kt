package com.ytone.longcare.features.location.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.common.utils.LogExt
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 定位跟踪UI状态
 */
data class LocationTrackingUiState(
    val isTracking: Boolean = false,
    val currentOrderId: Long = 0L,
    val hasLocationPermission: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * 定位跟踪ViewModel
 */
@HiltViewModel
class LocationTrackingViewModel @Inject constructor(
    private val locationTrackingManager: LocationTrackingManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LocationTrackingUiState())
    val uiState: StateFlow<LocationTrackingUiState> = _uiState.asStateFlow()

    private val _showPermissionDialog = MutableStateFlow(false)
    val showPermissionDialog: StateFlow<Boolean> = _showPermissionDialog.asStateFlow()

    init {
        // 监听定位管理器状态变化
        combine(
            locationTrackingManager.isTracking,
            locationTrackingManager.currentOrderId
        ) { isTracking, orderId ->
            _uiState.value = _uiState.value.copy(
                isTracking = isTracking,
                currentOrderId = orderId,
                hasLocationPermission = locationTrackingManager.hasLocationPermission()
            )
        }.launchIn(viewModelScope)
    }

    /**
     * 开始定位跟踪
     */
    fun startTracking(orderId: Long) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

                // 检查权限
                if (!locationTrackingManager.hasLocationPermission()) {
                    _showPermissionDialog.value = true
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // 启动跟踪
                val success = locationTrackingManager.startTracking(orderId)
                if (!success) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "启动定位跟踪失败，请检查权限设置"
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    LogExt.d("LocationTrackingViewModel", "Successfully started tracking for order: $orderId")
                }
            } catch (e: Exception) {
                LogExt.e("LocationTrackingViewModel", "Error starting tracking", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "启动定位跟踪时发生错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 停止定位跟踪
     */
    fun stopTracking() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                locationTrackingManager.stopTracking()
                _uiState.value = _uiState.value.copy(isLoading = false)
                LogExt.d("LocationTrackingViewModel", "Successfully stopped tracking")
            } catch (e: Exception) {
                LogExt.e("LocationTrackingViewModel", "Error stopping tracking", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "停止定位跟踪时发生错误: ${e.message}"
                )
            }
        }
    }

    /**
     * 权限请求结果处理
     */
    fun onPermissionResult(granted: Boolean, orderId: Long) {
        _showPermissionDialog.value = false
        if (granted) {
            startTracking(orderId)
        } else {
            _uiState.value = _uiState.value.copy(
                errorMessage = "需要位置权限才能开始定位跟踪"
            )
        }
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * 获取需要的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
        return locationTrackingManager.getRequiredPermissions()
    }

    /**
     * 检查权限状态
     */
    fun checkPermissionStatus() {
        _uiState.value = _uiState.value.copy(
            hasLocationPermission = locationTrackingManager.hasLocationPermission()
        )
    }
}