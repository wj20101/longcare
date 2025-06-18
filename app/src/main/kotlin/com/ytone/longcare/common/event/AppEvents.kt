package com.ytone.longcare.common.event

import android.content.Intent

/**
 * 定义应用内的全局事件。
 * 使用密封接口可以清晰地管理所有事件类型。
 */
sealed interface AppEvent {
    data object ForceLogout : AppEvent
    data class NfcIntentReceived(val intent: Intent) : AppEvent
    // 未来可以添加其他全局事件，例如网络连接断开等
}