package com.ytone.longcare.features.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.domain.login.LoginRepository
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.home.api.HomeExperience
import com.ytone.longcare.features.home.api.resolveHomeExperience
import com.ytone.longcare.features.home.reporting.HomeLoginLogInfoProvider
import com.ytone.longcare.model.CurrentUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class HomeUiState(
    val user: CurrentUser? = null,
    val experience: HomeExperience = HomeExperience.Loading,
    val selectedDashboardTab: Int = 0,
)

/** Home-screen state holder. Child screens receive values and events, never this ViewModel. */
@HiltViewModel
internal class HomeSharedViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val loginRepository: LoginRepository,
    private val homeLoginLogInfoProvider: HomeLoginLogInfoProvider,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = mutableUiState.asStateFlow()
    private var reportHomeEntryJob: Job? = null
    private var hasReportedHomeEntry = false

    init {
        viewModelScope.launch {
            userSessionRepository.sessionState.collect { session ->
                val nextUser = when (session) {
                    is SessionState.LoggedIn -> session.user
                    SessionState.LoggedOut,
                    SessionState.Unknown,
                    -> null
                }
                mutableUiState.update { previous ->
                    val isSameUser = previous.user?.scopeKey == nextUser?.scopeKey
                    HomeUiState(
                        user = nextUser,
                        experience = resolveHomeExperience(nextUser?.userIdentity),
                        selectedDashboardTab = if (isSameUser) {
                            previous.selectedDashboardTab
                        } else {
                            0
                        },
                    )
                }
            }
        }
    }

    fun selectDashboardTab(index: Int) {
        mutableUiState.update { it.copy(selectedDashboardTab = index) }
    }

    fun reportHomeEntry() {
        if (hasReportedHomeEntry || reportHomeEntryJob?.isActive == true) {
            return
        }

        hasReportedHomeEntry = true
        reportHomeEntryJob = viewModelScope.launch {
            recordHomeEntry()
        }
    }

    internal suspend fun recordHomeEntry() {
        try {
            val payload = homeLoginLogInfoProvider.build()
            loginRepository.recordLoginLog(
                phoneSystem = payload.phoneSystem,
                phoneVersion = payload.phoneVersion,
                networkType = payload.networkType,
                networkOperator = payload.networkOperator,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            // 登录日志不得阻断 Home；记录失败以便诊断，同一 ViewModel 生命周期内也不重复发送。
            CrashReportGateway.postCaughtException(error)
        }
    }
}
