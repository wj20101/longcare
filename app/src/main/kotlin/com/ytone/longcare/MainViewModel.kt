package com.ytone.longcare

import androidx.lifecycle.ViewModel
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * 这个 ViewModel 作为 MainActivity 的 ViewModel，
 * 它的唯一职责是暴露用户会话状态。
 */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionState> = userSessionRepository.sessionState

    fun forceLogout() {
        userSessionRepository.logout()
    }
}