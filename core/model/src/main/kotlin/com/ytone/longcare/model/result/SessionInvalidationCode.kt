package com.ytone.longcare.model.result

/**
 * 服务端约定的会话失效业务码。
 *
 * 该约定同时被全局响应适配器和后台任务使用，必须保持单一来源。
 */
object SessionInvalidationCode {
    const val INVALID_SESSION = 1001
    const val SESSION_EXPIRED = 3002

    fun requiresLogout(code: Int): Boolean =
        code == INVALID_SESSION || code == SESSION_EXPIRED
}
