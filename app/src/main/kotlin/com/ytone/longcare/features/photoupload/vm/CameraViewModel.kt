package com.ytone.longcare.features.photoupload.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationFacade
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

sealed interface CameraLocationState {
    data object Loading : CameraLocationState
    data object Unavailable : CameraLocationState
    data object Failed : CameraLocationState
    data class Coordinates(val value: String) : CameraLocationState
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    private val locationFacade: LocationFacade,
    private val imagePipeline: UnifiedImagePipeline,
) : ViewModel() {

    private val _location = MutableStateFlow<CameraLocationState>(CameraLocationState.Loading)
    val location: StateFlow<CameraLocationState> = _location

    private val _time = MutableStateFlow("")
    val time: StateFlow<String> = _time

    private val _syLogoImg = MutableStateFlow("")
    val syLogoImg = _syLogoImg.asStateFlow()

    fun updateCurrentLocationInfo() {
        viewModelScope.launch {
            try {
                val locationResult = locationFacade.getCurrentLocation()
                _location.value = if (locationResult != null) {
                    CameraLocationState.Coordinates(
                        "${locationResult.longitude},${locationResult.latitude}",
                    )
                } else {
                    CameraLocationState.Unavailable
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _location.value = CameraLocationState.Failed
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

    suspend fun processCapturedImage(request: WatermarkedCaptureRequest) =
        imagePipeline.processWatermarkedCapture(request)

}
