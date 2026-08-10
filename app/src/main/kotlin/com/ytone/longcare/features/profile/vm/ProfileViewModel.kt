package com.ytone.longcare.features.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.model.NurseServiceTimeModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.ui.message.UiMessageQueue
import com.ytone.longcare.domain.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _statsState = MutableStateFlow(NurseServiceTimeModel())
    val statsState: StateFlow<NurseServiceTimeModel> = _statsState.asStateFlow()
    private val messageQueue = UiMessageQueue()
    val uiMessages = messageQueue.messages

    fun consumeUiMessage(id: Long) = messageQueue.consume(id)


    fun refreshStats() {
        viewModelScope.launch {

            // 使用 profileRepository 调用接口
            when (val result = profileRepository.getServiceStatistics()) {
                is ApiResult.Success -> {
                    _statsState.value = result.data
                }
                is ApiResult.Failure -> {
                    messageQueue.enqueue(result.message)
                    logE("获取统计数据失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    messageQueue.enqueue(result.exception.message ?: "网络异常，请稍后重试")
                    logE("获取统计数据异常", throwable = result.exception)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            profileRepository.logout()
        }
    }
}
