package com.ytone.longcare.features.userlist.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.UserInfoModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.domain.userlist.UserListRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val userListRepository: UserListRepository,
) : ViewModel() {

    private val _userListState = MutableStateFlow<List<UserInfoModel>>(emptyList())
    val userListState: StateFlow<List<UserInfoModel>> = _userListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    /**
     * 获取已服务工时用户列表
     */
    fun getHaveServiceUserList() {
        viewModelScope.launch {
            _isLoading.value = true
            
            when (val result = userListRepository.getHaveServiceUserList()) {
                is ApiResult.Success -> {
                    _userListState.value = result.data
                }
                is ApiResult.Failure -> {
                    messageQueue.enqueue(result.message)
                    logE("获取已服务用户列表失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    messageQueue.enqueue(result.exception.message ?: "网络异常，请稍后重试")
                    logE("获取已服务用户列表异常", throwable = result.exception)
                }
            }
            
            _isLoading.value = false
        }
    }

    /**
     * 获取未服务工时用户列表
     */
    fun getNoServiceUserList() {
        viewModelScope.launch {
            _isLoading.value = true
            
            when (val result = userListRepository.getNoServiceUserList()) {
                is ApiResult.Success -> {
                    _userListState.value = result.data
                }
                is ApiResult.Failure -> {
                    messageQueue.enqueue(result.message)
                    logE("获取未服务用户列表失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    messageQueue.enqueue(result.exception.message ?: "网络异常，请稍后重试")
                    logE("获取未服务用户列表异常", throwable = result.exception)
                }
            }
            
            _isLoading.value = false
        }
    }

}
