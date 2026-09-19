package com.ytone.longcare.features.photoupload.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.domain.location.LocationFacade
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    private val systemConfigManager: WatermarkConfigProvider,
    private val locationFacade: LocationFacade,
    private val imagePipeline: UnifiedImagePipeline,
) : ViewModel() {

    private val _location = MutableStateFlow<CameraLocationState>(CameraLocationState.Loading)
    val location: StateFlow<CameraLocationState> = _location

    private val _time = MutableStateFlow("")
    val time: StateFlow<String> = _time

    private val _syLogoImg = MutableStateFlow("")
    val syLogoImg = _syLogoImg.asStateFlow()

    private var locationJob: Job? = null
    private var locationRequestId = 0L

    fun updateCurrentLocationInfo(hasLocationPermission: Boolean = true) {
        val requestId = ++locationRequestId
        locationJob?.cancel()
        _location.value = if (hasLocationPermission) CameraLocationState.Loading else CameraLocationState.Unavailable
        if (!hasLocationPermission) return
        locationJob = viewModelScope.launch {
            try {
                val locationResult = locationFacade.getCurrentLocation()
                if (requestId != locationRequestId) return@launch
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
                if (requestId == locationRequestId) _location.value = CameraLocationState.Failed
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
