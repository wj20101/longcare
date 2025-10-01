package com.ytone.longcare.features.photoupload.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val compositeLocationProvider: CompositeLocationProvider
) : ViewModel() {

    private val _location = MutableStateFlow("正在获取定位...")
    val location: StateFlow<String> = _location

    fun getCurrentLocationInfo() {
        viewModelScope.launch {
            try {
                val locationResult = compositeLocationProvider.getCurrentLocation()
                _location.value = if (locationResult != null) {
                    "卫星定位:${locationResult.longitude},${locationResult.latitude}"
                } else {
                    "未获取到定位"
                }
            } catch (e: Exception) {
                _location.value = "获取定位失败"
            }
        }
    }
}
