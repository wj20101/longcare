package com.ytone.longcare.features.userlist.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.UserInfoModel
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.userlist.UserListRepository
import com.ytone.longcare.common.utils.ToastHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserListViewModel @Inject constructor(
    private val userListRepository: UserListRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    private val _userListState = MutableStateFlow<List<UserInfoModel>>(emptyList())
    val userListState: StateFlow<List<UserInfoModel>> = _userListState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
                    toastHelper.showShort(result.message)
                    logE("获取已服务用户列表失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    toastHelper.showShort("网络异常，请稍后重试")
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
                    toastHelper.showShort(result.message)
                    logE("获取未服务用户列表失败: code=${result.code}, msg=${result.message}")
                }
                is ApiResult.Exception -> {
                    toastHelper.showShort("网络异常，请稍后重试")
                    logE("获取未服务用户列表异常", throwable = result.exception)
                }
            }
            
            _isLoading.value = false
        }
    }
}