package com.ytone.longcare.features.profile.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.NurseServiceTimeModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.isSuccess
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logE
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
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _statsState = MutableStateFlow(NurseServiceTimeModel())
    val statsState: StateFlow<NurseServiceTimeModel> = _statsState.asStateFlow()


    fun refreshStats() {
        viewModelScope.launch {

            // 使用 profileRepository 调用接口
            when (val result = profileRepository.getServiceStatistics()) {
                is ApiResult.Success -> {
                    _statsState.value = result.data
                }
                is ApiResult.Failure -> {
                    toastHelper.showShort(result.message)
                    logE("获取统计数据失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    logE("获取统计数据异常", throwable = result.exception)
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            val result = profileRepository.logout()
            if (result.isSuccess()) toastHelper.showShort("退出登录成功")
        }
    }
}