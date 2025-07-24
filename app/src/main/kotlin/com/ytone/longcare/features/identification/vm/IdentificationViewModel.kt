package com.ytone.longcare.features.identification.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.features.identification.ui.IdentificationState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IdentificationViewModel @Inject constructor() : ViewModel() {
    
    // 身份认证状态
    private val _identificationState = MutableStateFlow(IdentificationState.INITIAL)
    val identificationState: StateFlow<IdentificationState> = _identificationState.asStateFlow()
    
    // 验证服务人员
    fun verifyServicePerson() {
        viewModelScope.launch {
            // 模拟验证过程
            // 实际应用中，这里应该调用人脸识别API或其他验证方法
            _identificationState.value = IdentificationState.SERVICE_VERIFIED
        }
    }
    
    // 验证老人
    fun verifyElder() {
        viewModelScope.launch {
            // 模拟验证过程
            // 实际应用中，这里应该调用人脸识别API或其他验证方法
            _identificationState.value = IdentificationState.ELDER_VERIFIED
        }
    }
    
    // 重置状态
    fun resetState() {
        _identificationState.value = IdentificationState.INITIAL
    }
}