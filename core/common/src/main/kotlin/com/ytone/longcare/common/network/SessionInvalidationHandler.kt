package com.ytone.longcare.common.network

import kotlinx.coroutines.flow.StateFlow

data class SessionInvalidation(
    val id: Long,
    val reason: String,
    val loggedOutGeneration: Long,
)

/** Token-free identity attached to the actual request that supplied its encrypted auth header. */
data class RequestSessionGeneration(val value: Long)

/**
 * 统一处理服务端会话失效。
 *
 * 实现必须独立发起会话清理；[invalidations] 仅用于向 UI 可靠传递一次性提示，
 * 不能作为执行退出登录的唯一依据。这样即使 UI 暂无订阅者，也不会漏掉登出动作。
 */
interface SessionInvalidationHandler {
    val invalidations: StateFlow<SessionInvalidation?>

    fun invalidate(reason: String, generation: Long)

    /** Return false for a queued event belonging to an older login; check before showing UI. */
    fun consume(id: Long): Boolean
}
