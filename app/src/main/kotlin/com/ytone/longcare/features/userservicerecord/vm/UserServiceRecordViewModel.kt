package com.ytone.longcare.features.userservicerecord.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.UserOrderModel
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
class UserServiceRecordViewModel @Inject constructor(
    private val userListRepository: UserListRepository,
) : ViewModel() {

    private val _serviceRecordListState = MutableStateFlow<List<UserOrderModel>>(emptyList())
    val serviceRecordListState: StateFlow<List<UserOrderModel>> = _serviceRecordListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)

    /**
     * 获取用户服务记录列表
     * @param userId 用户ID
     */
    fun getUserServiceRecords(userId: Long) {
        viewModelScope.launch {
            _isLoading.value = true
            
            when (val result = userListRepository.getUserOrderList(userId)) {
                is ApiResult.Success -> {
                    _serviceRecordListState.value = result.data
                }
                is ApiResult.Failure -> {
                    messageQueue.enqueue(result.message)
                    logE("获取用户服务记录失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    messageQueue.enqueue(result.exception.message ?: "网络异常，请稍后重试")
                    logE("获取用户服务记录异常", throwable = result.exception)
                }
            }
            
            _isLoading.value = false
        }
    }

    /**
     * 刷新服务记录列表
     * @param userId 用户ID
     */
    fun refreshServiceRecords(userId: Long) {
        getUserServiceRecords(userId)
    }
}
