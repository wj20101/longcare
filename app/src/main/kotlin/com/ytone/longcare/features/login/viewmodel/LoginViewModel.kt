package com.ytone.longcare.features.login.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.LongCareApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val apiService: LongCareApiService) : ViewModel() {

    /**
     * 登录
     * @param mobile 手机号
     * @param code 验证码
     */
    fun login(mobile: String, code: String) {
        viewModelScope.launch {

        }
    }

    /**
     * 发送短信验证码
     * @param mobile 手机号
     */
    fun sendSmsCode(mobile: String) {
        viewModelScope.launch {

        }
    }
}