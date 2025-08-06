package com.ytone.longcare

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ytone.longcare.api.response.AppVersionModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.worker.UpdateWorker
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
    private val workManager: WorkManager
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = userSessionRepository.sessionState

    private val _appVersionModel = MutableStateFlow<AppVersionModel?>(null)
    val appVersionModel = _appVersionModel.asStateFlow()

    init {
        viewModelScope.launch {
            val updateWorkRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            workManager.enqueue(updateWorkRequest)
        }
    }

    fun setAppVersionModel(appVersionModel: AppVersionModel) {
        _appVersionModel.value = appVersionModel
    }

    fun clearAppVersionModel() {
        _appVersionModel.value = null
    }

    fun forceLogout() {
        userSessionRepository.logout()
    }
}