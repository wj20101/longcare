package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.tracker.CountdownAlarmDiagnostics
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
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
        CountdownAlarmDiagnostics.receiverEntered(intent, context)
        val result = goAsync()
        applicationScope.launch {
            try {
                CountdownAlarmDiagnostics.validationStarted(intent)
                countdownNotificationManager.onAlarmTriggered(intent)
            } finally {
                result.finish()
                // The Service may already have its own parcelled Bundle. Emit one independent
                // finish event, correlated by the original identity; finish() is not a service ack.
                val data = CountdownAlarmDiagnostics.receiverFinished(intent)
                val identityAt = intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L)
                CountdownEventTracker.trackEvent(
                    CountdownEventTracker.EventType.ALARM_RECEIVER_FINISHED,
                    CountdownNotificationManager.extractOrderKey(intent).orderId,
                    CountdownAlarmDiagnostics.fields(data, identityAt, "receiver_finish_returned"),
                    reportToServer = true,
                    userId = CountdownAlarmDiagnostics.userId(data, intent.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER)),
                )
            }
        }
    }
}
