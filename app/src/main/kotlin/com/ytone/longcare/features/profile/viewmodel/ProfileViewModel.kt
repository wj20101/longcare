package com.ytone.longcare.features.profile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.network.isSuccess
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.profile.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val toastHelper: ToastHelper
) : ViewModel() {

    fun logout() {
        viewModelScope.launch {
            val result = profileRepository.logout()
            if (result.isSuccess()) toastHelper.showShort("退出登录成功")
        }
    }
}