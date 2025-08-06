package com.ytone.longcare.common.event

import android.content.Intent
import com.ytone.longcare.api.response.AppVersionModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 一个单例的事件总线，用于在应用内广播全局事件。
 */
@Singleton
class AppEventBus @Inject constructor() {
    // 创建一个私有的、可变的 SharedFlow
    private val _events = MutableSharedFlow<AppEvent>()

    // 对外暴露一个不可变的 SharedFlow，供订阅者使用
    val events = _events.asSharedFlow()

    /**
     * 发送一个全局事件。
     * @param event 要发送的事件。
     */
    suspend fun send(event: AppEvent) {
        _events.emit(event)
    }
}

sealed class AppEvent {
    data class ForceLogout(val reason: String) : AppEvent()
    data class NfcIntentReceived(val intent: Intent) : AppEvent()
    data class AppUpdate(val appVersionModel: AppVersionModel) : AppEvent()
}