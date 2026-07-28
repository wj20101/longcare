package com.ytone.longcare.common.network

import kotlinx.coroutines.flow.StateFlow

data class SessionInvalidation(
    val id: Long,
    val reason: String,
)

/**
 * 统一处理服务端会话失效。
 *
 * 实现必须独立发起会话清理；[invalidations] 仅用于向 UI 可靠传递一次性提示，
 * 不能作为执行退出登录的唯一依据。这样即使 UI 暂无订阅者，也不会漏掉登出动作。
 */
interface SessionInvalidationHandler {
    val invalidations: StateFlow<SessionInvalidation?>

    fun invalidate(reason: String)

    fun consume(id: Long)
}
