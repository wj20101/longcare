package com.ytone.longcare.features.login.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.LongCareApiService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(private val apiService: LongCareApiService) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

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

    sealed class LoginUiState {
        data object Idle : LoginUiState()
        data object Loading : LoginUiState()
        data class Success(val message: String) : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }
}