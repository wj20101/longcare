package com.ytone.longcare.features.login.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.LoginResultModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginRepository: LoginRepository,
    private val userSessionRepository: UserSessionRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    private val _sendSmsCodeState = MutableStateFlow<SendSmsCodeUiState>(SendSmsCodeUiState.Idle)
    val sendSmsCodeState: StateFlow<SendSmsCodeUiState> = _sendSmsCodeState

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds
    private var countdownJob: Job? = null

    /**
     * 发送短信验证码
     */
    private fun isValidMobileNumber(mobile: String): Boolean {
        val regex = "^1[3-9]\\d{9}$"
        return mobile.matches(regex.toRegex())
    }

    fun sendSmsCode(mobile: String) {
        if (!isValidMobileNumber(mobile)) {
            showShortToast("请输入有效的11位手机号")
            return
        }
        viewModelScope.launch {
            _sendSmsCodeState.value = SendSmsCodeUiState.Loading
            when (val result = loginRepository.sendSmsCode(mobile)) {
                is ApiResult.Success -> {
                    _sendSmsCodeState.value = SendSmsCodeUiState.Success
                    showShortToast("验证码已发送")
                    startCountdown()
                }

                is ApiResult.Failure -> {
                    val errorMessage = "发送失败: ${result.message}"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(errorMessage)
                    showShortToast(errorMessage)
                }

                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(exceptionMessage)
                    showShortToast(exceptionMessage)
                }
            }
        }
    }

    /**
     * 执行登录
     */
    fun login(mobile: String, code: String) {
        if (!isValidMobileNumber(mobile) || code.isBlank()) {
            showShortToast("手机号或验证码格式不正确")
            return
        }
        viewModelScope.launch {
            _loginState.value = LoginUiState.Loading
            when (val result = loginRepository.login(mobile, code)) {
                is ApiResult.Success -> {
                    val loginResult = result.data
                    // 登录成功，转换并保存User对象
                    val user = loginResult.toUser()
                    userSessionRepository.login(user)

                    _loginState.value = LoginUiState.Success(user)
                    showShortToast("登录成功")
                }

                is ApiResult.Failure -> {
                    val errorMessage = "登录失败: ${result.message}"
                    _loginState.value = LoginUiState.Error(errorMessage)
                    showShortToast(errorMessage)
                }

                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _loginState.value = LoginUiState.Error(exceptionMessage)
                    showShortToast(exceptionMessage)
                }
            }
        }
    }

    private fun showShortToast(msg: CharSequence) {
        toastHelper.showShort(msg)
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            _countdownSeconds.value = SMS_TIME_TOTAL
            while (_countdownSeconds.value > 0) {
                delay(1000L)
                _countdownSeconds.value--
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }

    companion object {
        // 短信倒计时长度
        private const val SMS_TIME_TOTAL = 60
    }
}

private fun LoginResultModel.toUser(): User {
    return User(
        userId = userId,
        userName = userName,
        headUrl = headUrl,
        userIdentity = userIdentity,
        identityCardNumber = identityCardNumber,
        gender = gender,
        token = token,
        companyId = companyId,
        accountId = accountId,
        faceId = faceId
    )
}

// --- UI 状态定义 ---
sealed class LoginUiState {
    data object Idle : LoginUiState()
    data object Loading : LoginUiState()
    data class Success(val user: User) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
}

sealed class SendSmsCodeUiState {
    data object Idle : SendSmsCodeUiState()
    data object Loading : SendSmsCodeUiState()
    data object Success : SendSmsCodeUiState()
    data class Error(val message: String) : SendSmsCodeUiState()
}