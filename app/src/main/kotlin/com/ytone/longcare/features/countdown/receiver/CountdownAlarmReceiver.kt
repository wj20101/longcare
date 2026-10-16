package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 系统闹钟异步入口；身份、发布及降级统一由 manager 处理。 */
@AndroidEntryPoint
class CountdownAlarmReceiver : BroadcastReceiver() {
    @Inject lateinit var countdownNotificationManager: CountdownNotificationManager
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        applicationScope.launch {
            try {
                countdownNotificationManager.onAlarmTriggered(intent)
            } finally {
                result.finish()
            }
        }
    }
}
