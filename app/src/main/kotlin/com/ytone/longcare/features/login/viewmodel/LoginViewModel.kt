package com.ytone.longcare.features.login.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor() : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    fun login(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            // Simulate network call
            delay(2000)
            if (phoneNumber == "13800138000" && password == "password") { // Dummy validation
                _loginState.value = LoginUiState.Success("登录成功")
            } else if (phoneNumber.isBlank() || password.isBlank()){
                _loginState.value = LoginUiState.Error("手机号或密码不能为空")
            } else {
                _loginState.value = LoginUiState.Error("手机号或密码错误")
            }
        }
    }

    sealed class LoginUiState {
        data object Idle : LoginUiState()
        data object Loading : LoginUiState()
        data class Success(val message: String) : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }
}