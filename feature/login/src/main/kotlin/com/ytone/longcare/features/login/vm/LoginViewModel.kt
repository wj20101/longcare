package com.ytone.longcare.features.login.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.LoginResultModel
import com.ytone.longcare.model.StartConfigResultModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.LoginPreferencesManager
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
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
    private val loginPreferencesManager: LoginPreferencesManager
) : ViewModel() {

    private val _loginState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val loginState: StateFlow<LoginUiState> = _loginState

    private val _sendSmsCodeState = MutableStateFlow<SendSmsCodeUiState>(SendSmsCodeUiState.Idle)
    val sendSmsCodeState: StateFlow<SendSmsCodeUiState> = _sendSmsCodeState

    private val _countdownSeconds = MutableStateFlow(0)
    val countdownSeconds: StateFlow<Int> = _countdownSeconds

    private val _feedback = MutableStateFlow<LoginFeedback?>(null)
    val feedback: StateFlow<LoginFeedback?> = _feedback
    
    private val _startConfigState = MutableStateFlow<StartConfigUiState>(StartConfigUiState.Idle)
    val startConfigState: StateFlow<StartConfigUiState> = _startConfigState
    
    private var countdownJob: Job? = null
    private var privacyAgreementConfirmed = false
    private var nextFeedbackId = 0L

    init {
        // Safe: LoginViewModel is only created after privacy consent (AppNavigation gates with return)
        loadStartConfig()
    }

    /**
     * 用户确认隐私协议后记录状态。
     */
    fun onPrivacyAgreementConfirmed() {
        privacyAgreementConfirmed = true
    }

    private fun loadStartConfig() {
        viewModelScope.launch {
            _startConfigState.value = StartConfigUiState.Loading
            when (val result = loginRepository.getStartConfig()) {
                is ApiResult.Success -> {
                    _startConfigState.value = StartConfigUiState.Success(result.data)
                }
                is ApiResult.Failure -> {
                    _startConfigState.value = StartConfigUiState.Error(result.message)
                }
                is ApiResult.Exception -> {
                    _startConfigState.value = StartConfigUiState.Error(result.exception.message ?: "网络异常")
                }
            }
        }
    }

    /**
     * 发送短信验证码
     */
    private fun isValidMobileNumber(mobile: String): Boolean {
        val regex = "^1[3-9]\\d{9}$"
        return mobile.matches(regex.toRegex())
    }

    fun sendSmsCode(mobile: String) {
        if (!privacyAgreementConfirmed) {
            publishFeedback("请先阅读并同意用户协议和隐私政策")
            return
        }
        if (!isValidMobileNumber(mobile)) {
            publishFeedback("请输入有效的11位手机号")
            return
        }
        viewModelScope.launch {
            _sendSmsCodeState.value = SendSmsCodeUiState.Loading
            when (val result = loginRepository.sendSmsCode(mobile)) {
                is ApiResult.Success -> {
                    _sendSmsCodeState.value = SendSmsCodeUiState.Success
                    publishFeedback("验证码已发送")
                    startCountdown()
                }

                is ApiResult.Failure -> {
                    val errorMessage = "发送失败: ${result.message}"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(errorMessage)
                    publishFeedback(errorMessage)
                }

                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _sendSmsCodeState.value = SendSmsCodeUiState.Error(exceptionMessage)
                    publishFeedback(exceptionMessage)
                }
            }
        }
    }

    /**
     * 执行登录
     */
    fun login(mobile: String, code: String) {
        if (!privacyAgreementConfirmed) {
            publishFeedback("请先阅读并同意用户协议和隐私政策")
            return
        }
        if (!isValidMobileNumber(mobile) || code.isBlank()) {
            publishFeedback("手机号或验证码格式不正确")
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
                    
                    // 保存登录成功的手机号码
                    loginPreferencesManager.saveLastLoginPhoneNumber(mobile)

                    _loginState.value = LoginUiState.Success(user)
                }

                is ApiResult.Failure -> {
                    val errorMessage = "登录失败: ${result.message}"
                    _loginState.value = LoginUiState.Error(errorMessage)
                    publishFeedback(errorMessage)
                }

                is ApiResult.Exception -> {
                    val exceptionMessage = result.exception.message ?: "网络异常"
                    _loginState.value = LoginUiState.Error(exceptionMessage)
                    publishFeedback(exceptionMessage)
                }
            }
        }
    }

    /**
     * 获取上次登录成功的手机号码
     */
    fun getLastLoginPhoneNumber(): String {
        return loginPreferencesManager.getLastLoginPhoneNumber()
    }

    fun consumeFeedback(id: Long) {
        if (_feedback.value?.id == id) {
            _feedback.value = null
        }
    }

    private fun publishFeedback(message: String) {
        _feedback.value = LoginFeedback(
            id = ++nextFeedbackId,
            message = message
        )
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

data class LoginFeedback(
    val id: Long,
    val message: String
)

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
        accountId = accountId
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

sealed class StartConfigUiState {
    data object Idle : StartConfigUiState()
    data object Loading : StartConfigUiState()
    data class Success(val data: StartConfigResultModel) : StartConfigUiState()
    data class Error(val message: String) : StartConfigUiState()
}
