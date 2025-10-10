package com.ytone.longcare.features.selectservice.vm

import androidx.lifecycle.ViewModel
import com.ytone.longcare.common.utils.SystemConfigManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SelectServiceViewModel @Inject constructor(val systemConfigManager: SystemConfigManager) : ViewModel()