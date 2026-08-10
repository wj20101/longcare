package com.ytone.longcare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.AppVersionModel
import com.ytone.longcare.worker.StartupUpdateWorkObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 这个 ViewModel 作为 MainActivity 的 ViewModel，
 * 它的唯一职责是暴露用户会话状态。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    startupUpdateWorkObserver: StartupUpdateWorkObserver,
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = userSessionRepository.sessionState

    private val _appVersionModel = MutableStateFlow<AppVersionModel?>(null)
    val appVersionModel = _appVersionModel.asStateFlow()
    private var dismissedVersionCode: Int? = null

    init {
        viewModelScope.launch {
            startupUpdateWorkObserver.availableUpdate.collect { update ->
                if (update?.versionCode != dismissedVersionCode) {
                    _appVersionModel.value = update
                }
            }
        }
    }

    fun clearAppVersionModel() {
        dismissedVersionCode = _appVersionModel.value?.versionCode
        _appVersionModel.value = null
    }
}
