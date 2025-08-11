package com.ytone.longcare.features.home.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.models.protos.User
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * 在 HomeScreen 作用域下共享的 ViewModel。
 * 负责提供用户状态并处理相关操作，如登出。
 */
@HiltViewModel
class HomeSharedViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    /**
     * 向 UI 暴露一个只包含 User 对象的状态流。
     * 当用户未登录时，它会是 null。
     * UI 层可以直接收集此状态。
     */
    val userState: StateFlow<User?> = userSessionRepository.sessionState.map {
        // 从 SessionState 中提取 User 对象
        when (it) {
            is SessionState.LoggedIn -> it.user
            else -> null
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null // 初始值为 null
    )

    // 主仪表盘Tab状态管理
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    /**
     * 更新选中的Tab索引
     */
    fun updateSelectedTabIndex(index: Int) {
        _selectedTabIndex.value = index
    }
}