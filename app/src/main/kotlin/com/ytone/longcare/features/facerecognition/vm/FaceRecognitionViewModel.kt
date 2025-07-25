package com.ytone.longcare.features.facerecognition.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 人脸识别指导页面的ViewModel
 */
@HiltViewModel
class FaceRecognitionViewModel @Inject constructor() : ViewModel() {
    
    // 是否同意隐私政策
    private val _privacyAgreed = MutableStateFlow(true)
    val privacyAgreed: StateFlow<Boolean> = _privacyAgreed.asStateFlow()
    
    // 更新隐私政策同意状态
    fun updatePrivacyAgreement(agreed: Boolean) {
        _privacyAgreed.value = agreed
    }
    
    // 开始人脸识别
    fun startFaceRecognition() {
        viewModelScope.launch {
            // 这里可以添加实际的人脸识别逻辑
            // 例如调用人脸识别SDK或API
        }
    }
}