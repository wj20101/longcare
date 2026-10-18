package com.ytone.longcare.features.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.home.HomeDeviceRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.ZhiChiDeviceStatusModel
import com.ytone.longcare.model.UserIdentityDisplay
import com.ytone.longcare.model.userIdentityDisplay
import com.ytone.longcare.model.result.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@HiltViewModel
class HomeDeviceViewModel @Inject constructor(
    private val repository: HomeDeviceRepository,
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {
    private val _deviceStatus = MutableStateFlow<ZhiChiDeviceStatusModel?>(null)
    val deviceStatus: StateFlow<ZhiChiDeviceStatusModel?> = _deviceStatus.asStateFlow()
    private val pollingMutex = Mutex()
    // Observe account changes even while the page is hidden; this never starts network work.
    private val deviceSession = userSessionRepository.sessionState
        .map { it.deviceSession() }
        .distinctUntilChanged()
        .onEach { _deviceStatus.value = null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The visible home page owns this coroutine; leaving it cancels the request and loop. */
    suspend fun pollDeviceStatus() = pollingMutex.withLock {
        deviceSession.collectLatest { session ->
            if (session == null) return@collectLatest

            while (userSessionRepository.sessionState.value.deviceSession() == session) {
                coroutineScope {
                    // Wait for both the request and its interval; slow reads never overlap or queue ticks.
                    launch { delay(POLL_INTERVAL_MILLIS) }
                    val status = fetchDeviceStatus()
                    currentCoroutineContext().ensureActive()
                    // Keep the last successful state during refresh, but never reuse it for another session.
                    if (status != null && userSessionRepository.sessionState.value.deviceSession() == session) {
                        _deviceStatus.value = status
                    }
                }
            }
        }
    }

    private suspend fun fetchDeviceStatus(): ZhiChiDeviceStatusModel? = try {
        when (val result = repository.getDeviceStatus()) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> null
            is ApiResult.Exception -> {
                if (result.exception is CancellationException) throw result.exception
                null
            }
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        // Keep the last successful state through a transient failure; the next scheduled read retries.
        null
    }

    private fun SessionState.deviceSession(): DeviceSession? = user
        ?.takeUnless { it.userIdentityDisplay() == UserIdentityDisplay.SALES }
        ?.let { DeviceSession(it.companyId, it.accountId, it.userId, it.userIdentity, it.token) }

    private data class DeviceSession(
        val companyId: Int,
        val accountId: Int,
        val userId: Int,
        val identity: Int,
        val token: String,
    )

    private companion object {
        const val POLL_INTERVAL_MILLIS = 5_000L
    }
}
