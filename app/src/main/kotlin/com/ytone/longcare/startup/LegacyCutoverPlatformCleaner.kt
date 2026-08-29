package com.ytone.longcare.startup

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.PendingIntentCompat
import androidx.work.WorkManager
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.service.ServiceTimeNotificationManager
import com.ytone.longcare.features.service.receiver.ServiceTimeAlarmReceiver
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class LegacyCutoverPlatformCleaner @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val workManagerProvider: Provider<WorkManager>,
) {
    internal suspend fun cleanup() {
        context.stopService(Intent(context, AlarmRingtoneService::class.java))
        context.stopService(Intent(context, CountdownForegroundService::class.java))

        workManagerProvider.get().cancelAllWork().result.get(30, TimeUnit.SECONDS)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            alarmManager.cancelAll()
        }
        cancelLegacyBroadcast(
            alarmManager,
            requestCode = LEGACY_SERVICE_ALARM_REQUEST_CODE,
            intent = Intent(context, ServiceTimeAlarmReceiver::class.java).apply {
                action = ServiceTimeNotificationManager.ACTION_SERVICE_TIME_END_ALARM
            },
        )
        cancelLegacyBroadcast(
            alarmManager,
            requestCode = LEGACY_COUNTDOWN_DISMISS_REQUEST_CODE,
            intent = Intent(context, DismissAlarmReceiver::class.java),
        )
        cancelLegacyActivity(
            requestCode = LEGACY_COUNTDOWN_ACTIVITY_REQUEST_CODE,
            intent = Intent(context, CountdownAlarmActivity::class.java),
        )

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    private fun cancelLegacyBroadcast(
        alarmManager: AlarmManager,
        requestCode: Int,
        intent: Intent,
    ) {
        PendingIntentCompat.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE,
            false,
        )?.let { pendingIntent ->
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun cancelLegacyActivity(requestCode: Int, intent: Intent) {
        PendingIntentCompat.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE,
            false,
        )?.cancel()
    }

    private companion object {
        const val LEGACY_COUNTDOWN_DISMISS_REQUEST_CODE = 3002
        const val LEGACY_COUNTDOWN_ACTIVITY_REQUEST_CODE = 3003
        const val LEGACY_SERVICE_ALARM_REQUEST_CODE = 5001
    }
}
