package com.ytone.longcare.domain.repository

import com.ytone.longcare.models.protos.User
import kotlinx.coroutines.flow.StateFlow

/**
 * 定义会话状态的密封类。
 * 通过一个抽象属性 `user` 提供对用户对象的便捷访问。
 */
sealed class SessionState {
    /**
     * 当前的用户对象。
     * 仅在 [LoggedIn] 状态下为非空，在 [Unknown] 和 [LoggedOut] 状态下为 null。
     */
    abstract val user: User?

    /**
     * 初始状态，正在从持久化存储中读取用户状态。
     */
    data object Unknown : SessionState() {
        override val user: User? = null
    }

    /**
     * 用户已登录状态，包含用户信息。
     * @param user 当前登录的 User 对象，不能为空。
     */
    data class LoggedIn(override val user: User) : SessionState()

    /**
     * 用户已登出状态。
     */
    data object LoggedOut : SessionState() {
        override val user: User? = null
    }
}


/**
 * 用户会话管理的接口
 */
interface UserSessionRepository {
    /**
     * 一个 [StateFlow]，发射当前的会话状态 [SessionState]。
     */
    val sessionState: StateFlow<SessionState>

    /**
     * 登录用户，并持久化用户信息。
     */
    fun login(user: User)

    /**
     * 更新当前登录的用户信息。
     */
    fun updateUser(user: User)

    /**
     * 退出登录，并清除持久化的用户信息。
     */
    fun logout()
}