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
    val isLocationEnabled: Boolean = false,
    val isLocationAvailable: Boolean = false,
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
            val hasPermission = locationTrackingManager.hasLocationPermission()
            val isEnabled = locationTrackingManager.isLocationEnabled()
            val isAvailable = locationTrackingManager.isLocationAvailable()
            
            _uiState.value = _uiState.value.copy(
                isTracking = isTracking,
                currentOrderId = orderId,
                hasLocationPermission = hasPermission,
                isLocationEnabled = isEnabled,
                isLocationAvailable = isAvailable
            )
        }.launchIn(viewModelScope)
    }

    /**
     * 开始定位跟踪
     */
    fun startTracking(orderId: Long) {
        LogExt.d("LocationTrackingViewModel", "startTracking called with orderId: $orderId")
        
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
                LogExt.d("LocationTrackingViewModel", "UI state set to loading")

                // 检查权限
                val hasPermission = locationTrackingManager.hasLocationPermission()
                LogExt.d("LocationTrackingViewModel", "Has location permission: $hasPermission")
                
                if (!hasPermission) {
                    LogExt.d("LocationTrackingViewModel", "Showing permission dialog")
                    _showPermissionDialog.value = true
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@launch
                }

                // 检查位置服务是否启用
                val isLocationEnabled = locationTrackingManager.isLocationEnabled()
                LogExt.d("LocationTrackingViewModel", "Is location enabled: $isLocationEnabled")
                
                if (!isLocationEnabled) {
                    LogExt.w("LocationTrackingViewModel", "Location service is disabled")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "位置服务未启用，请在系统设置中开启位置服务"
                    )
                    return@launch
                }

                // 启动跟踪
                LogExt.d("LocationTrackingViewModel", "Calling locationTrackingManager.startTracking")
                val success = locationTrackingManager.startTracking(orderId)
                LogExt.d("LocationTrackingViewModel", "startTracking result: $success")
                
                if (!success) {
                    LogExt.e("LocationTrackingViewModel", "Failed to start tracking")
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "启动定位跟踪失败，请检查权限和位置服务设置"
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
        LogExt.d("LocationTrackingViewModel", "Permission result: granted=$granted, orderId=$orderId")
        _showPermissionDialog.value = false
        if (granted) {
            LogExt.d("LocationTrackingViewModel", "Permission granted, restarting tracking")
            startTracking(orderId)
        } else {
            LogExt.w("LocationTrackingViewModel", "Permission denied")
            _uiState.value = _uiState.value.copy(
                errorMessage = "需要位置权限才能开始定位跟踪"
            )
        }
    }

    /**
     * 检查位置服务状态并返回用户友好的提示信息
     */
    fun getLocationStatusMessage(): String? {
        return when {
            !locationTrackingManager.hasLocationPermission() -> "需要位置权限才能使用定位功能"
            !locationTrackingManager.isLocationEnabled() -> "位置服务未启用，请在系统设置中开启"
            else -> null
        }
    }

    /**
     * 检查是否可以开始定位跟踪
     */
    fun canStartTracking(): Boolean {
        return locationTrackingManager.isLocationAvailable()
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * 隐藏权限对话框
     */
    fun hidePermissionDialog() {
        _showPermissionDialog.value = false
    }

    /**
     * 重置状态
     */
    fun resetState() {
        locationTrackingManager.resetState()
        _uiState.value = LocationTrackingUiState()
        _showPermissionDialog.value = false
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