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
     */
    fun login(phoneNumber: String, code: String) {
        viewModelScope.launch {

        }
    }

    /**
     * 发送短信验证码
     */
    fun sendSmsCode(){
        viewModelScope.launch {

        }
    }
}