package com.ytone.longcare.features.photoupload.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.image.UnifiedImagePipeline
import com.ytone.longcare.common.image.WatermarkedCaptureRequest
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.domain.system.WatermarkConfigProvider
import com.ytone.longcare.model.LocationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal data class CaptureStamp(val time: String, val location: LocationResult)

internal fun LocationResult.watermarkCoordinates(): String = "$longitude,$latitude"

sealed interface CameraLocationState {
    data object Loading : CameraLocationState
    data object Unavailable : CameraLocationState
    data object Failed : CameraLocationState
    data class Coordinates(val sample: LocationResult) : CameraLocationState {
        val value: String get() = sample.watermarkCoordinates()
    }
}

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val systemConfigManager: WatermarkConfigProvider,
    private val locationFacade: LocationFacade,
    private val imagePipeline: UnifiedImagePipeline,
    private val sessions: UserSessionRepository,
) : ViewModel() {
    private val _location = MutableStateFlow<CameraLocationState>(CameraLocationState.Loading)
    val location = _location.asStateFlow()

    private val _time = MutableStateFlow("")
    val time = _time.asStateFlow()

    private val _syLogoImg = MutableStateFlow("")
    val syLogoImg = _syLogoImg.asStateFlow()

    private val sessionGeneration = sessions.sessionSnapshot.value.generation
    private var active = false
    private var locationRequest: Deferred<LocationAcquisition>? = null

    fun onCameraResume(hasFinePermission: Boolean) {
        active = true
        updateTime()
        viewModelScope.launch { _syLogoImg.value = systemConfigManager.getSyLogoImg() }
        if (hasFinePermission) {
            requestLocation()
        } else {
            locationRequest?.cancel()
            locationRequest = null
            _location.value = CameraLocationState.Unavailable
        }
    }

    fun onCameraPause() {
        active = false
        locationRequest?.cancel()
        locationRequest = null
        _location.value = CameraLocationState.Unavailable
    }

    internal fun isCurrentAccount(): Boolean {
        val session = sessions.sessionSnapshot.value
        return session.user != null && session.generation == sessionGeneration
    }

    internal suspend fun awaitAccountChange() {
        sessions.sessionSnapshot.first { it.generation != sessionGeneration }
    }

    internal fun ensureCameraSession() {
        if (!active || !isCurrentAccount()) throw CancellationException("Camera session ended")
    }

    private fun requestLocation(): Deferred<LocationAcquisition> {
        locationRequest?.takeIf { it.isActive }?.let { return it }
        _location.value = CameraLocationState.Loading
        return viewModelScope.async {
            ensureCameraSession()
            val result = try {
                locationFacade.acquireCurrentLocation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                ensureCameraSession()
                _location.value = CameraLocationState.Failed
                return@async LocationAcquisition.Failure(LocationFailure.UNAVAILABLE)
            }
            ensureActive()
            ensureCameraSession()
            _location.value = when (result) {
                is LocationAcquisition.Success -> CameraLocationState.Coordinates(result.location)
                is LocationAcquisition.Failure -> CameraLocationState.Unavailable
            }
            result
        }.also { locationRequest = it }
    }

    internal suspend fun awaitCountdown(delaySeconds: Int, onCountdownTick: (Int) -> Unit) {
        ensureCameraSession()
        for (seconds in delaySeconds downTo 1) {
            onCountdownTick(seconds)
            delay(1000)
        }
        onCountdownTick(0)
        ensureCameraSession()
    }

    internal suspend fun shutterStamp(
        prepareWatermark: () -> Boolean,
        onFailure: (LocationFailure) -> Unit,
    ): CaptureStamp? {
        ensureCameraSession()
        val cached = (_location.value as? CameraLocationState.Coordinates)?.sample
        if (cached != null && locationFacade.isUsable(cached)) {
            if (!prepareWatermark()) return null
            checkedStamp(cached)?.let { return it }
        }
        // A cached fix may have expired while drawing the logo. Acquire once and sample the logo again.
        val result = requestLocation().await()
        currentCoroutineContext().ensureActive()
        ensureCameraSession()
        if (result is LocationAcquisition.Success) {
            if (!prepareWatermark()) return null
            checkedStamp(result.location)?.let { return it }
            _location.value = CameraLocationState.Unavailable
        }
        val failure = (result as? LocationAcquisition.Failure)?.reason ?: LocationFailure.QUALITY
        onFailure(failure)
        return null
    }

    private fun checkedStamp(sample: LocationResult): CaptureStamp? {
        val stamp = CaptureStamp(updateTime(), sample)
        val usable = locationFacade.isUsable(sample)
        ensureCameraSession()
        return stamp.takeIf { usable }
    }

    private fun updateTime(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()).also { _time.value = it }

    internal suspend fun processCapturedImage(request: WatermarkedCaptureRequest) =
        imagePipeline.processWatermarkedCapture(request)
}
