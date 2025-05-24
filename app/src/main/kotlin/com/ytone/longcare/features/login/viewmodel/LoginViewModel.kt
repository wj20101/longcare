package com.ytone.longcare.features.login.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    sealed class LoginUiState {
        data object Idle : LoginUiState()
        data object Loading : LoginUiState()
        data object Success : LoginUiState()
        data class Error(val message: String) : LoginUiState()
    }

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    fun login(phoneNumber: String, password: String) {
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            // Simulate network call
            kotlinx.coroutines.delay(2000)
            if (phoneNumber == "12345678900" && password == "password") {
                userSessionRepository.loginUser(phoneNumber) // Use phone number as user ID for simplicity
                _loginState.value = LoginUiState.Success
            } else {
                _loginState.value = LoginUiState.Error("手机号或密码错误")
            }
        }
    }

    fun logout() {
        userSessionRepository.logoutUser()
        // Optionally, navigate back to login screen or update UI state
        _loginState.value = LoginUiState.Idle // Reset state after logout
    }
}