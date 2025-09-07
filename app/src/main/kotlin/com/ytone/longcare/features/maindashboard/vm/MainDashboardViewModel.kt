package com.ytone.longcare.features.maindashboard.vm

import androidx.lifecycle.ViewModel
import com.ytone.longcare.common.utils.SystemConfigManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainDashboardViewModel @Inject constructor(
    val systemConfigManager: SystemConfigManager
) : ViewModel() {

}