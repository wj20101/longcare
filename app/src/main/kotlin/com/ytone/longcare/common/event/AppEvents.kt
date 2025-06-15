package com.ytone.longcare.common.event

/**
 * 定义应用内的全局事件。
 * 使用密封接口可以清晰地管理所有事件类型。
 */
sealed interface AppEvent {
    data object ForceLogout : AppEvent
    // 未来可以添加其他全局事件，例如网络连接断开等
}