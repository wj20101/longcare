package com.ytone.longcare.features.photoupload.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.features.location.core.LocationFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val locationFacade: LocationFacade
) : ViewModel() {

    private val _location = MutableStateFlow("正在获取定位...")
    val location: StateFlow<String> = _location

    private val _time = MutableStateFlow("")
    val time: StateFlow<String> = _time

    private val _syLogoImg = MutableStateFlow("")
    val syLogoImg = _syLogoImg.asStateFlow()

    fun updateCurrentLocationInfo() {
        viewModelScope.launch {
            try {
                val locationResult = locationFacade.getCurrentLocation()
                _location.value = if (locationResult != null) {
                    "${locationResult.longitude},${locationResult.latitude}"
                } else {
                    "未获取到定位"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _location.value = "获取定位失败"
            }
        }
    }

    fun updateTime() {
        _time.value = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    }

    fun updateSyLogoImg(){
        viewModelScope.launch {
            _syLogoImg.value = systemConfigManager.getSyLogoImg()
        }
    }

}
