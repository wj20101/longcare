package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ytone.longcare.model.OrderKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 倒计时通知管理器
 * 负责管理倒计时完成时的通知和AlarmManager
 */
@Singleton
class CountdownNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager
) {

    companion object {
        private const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel_v2"
        private const val COUNTDOWN_NOTIFICATION_ID = 2001
        private const val COUNTDOWN_ALARM_REQUEST_CODE = 3001
        private const val DISMISS_ALARM_REQUEST_CODE = 3002
        private const val COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE = 3003

        const val EXTRA_ORDER_KEY = "extra_order_key"
        private const val EXTRA_ORDER_KEY_LEGACY = "extra_request"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        private const val EXTRA_SERVICE_NAME_LEGACY = "service_name"

        private const val PREFS_NAME = "countdown_notification_prefs"
        private const val KEY_LAST_SCHEDULED_ORDER_ID = "key_last_scheduled_order_id"
        private const val NO_ORDER_ID = -1L
        private const val ACTION_COUNTDOWN_ALARM_PREFIX = "COUNTDOWN_ALARM_"

        fun extractOrderKey(
            intent: Intent?,
            defaultValue: OrderKey = OrderKey(orderId = -1L, planId = 0)
        ): OrderKey {
            if (intent == null) {
                return defaultValue
            }
            return extractSerializableOrderKey(intent, EXTRA_ORDER_KEY)
                ?: extractSerializableOrderKey(intent, EXTRA_ORDER_KEY_LEGACY)
                ?: defaultValue
        }

        fun extractServiceName(intent: Intent?, defaultValue: String): String {
            if (intent == null) {
                return defaultValue
            }
            return intent.getStringExtra(EXTRA_SERVICE_NAME)
                ?: intent.getStringExtra(EXTRA_SERVICE_NAME_LEGACY)
                ?: defaultValue
        }

        private fun extractSerializableOrderKey(intent: Intent, key: String): OrderKey? {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getSerializableExtra(key, OrderKey::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getSerializableExtra(key) as? OrderKey
            }
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val alarmDelegate by lazy {
        CountdownAlarmDelegate(
            context = context,
            alarmManager = alarmManager,
            prefs = prefs,
            countdownAlarmRequestCode = COUNTDOWN_ALARM_REQUEST_CODE,
            countdownAlarmActivityRequestCode = COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
            actionCountdownAlarmPrefix = ACTION_COUNTDOWN_ALARM_PREFIX,
            keyLastScheduledOrderId = KEY_LAST_SCHEDULED_ORDER_ID,
            noOrderId = NO_ORDER_ID,
        )
    }

    private val notificationUiDelegate by lazy {
        CountdownNotificationUiDelegate(
            context = context,
            notificationManager = notificationManager,
            channelId = COUNTDOWN_NOTIFICATION_CHANNEL_ID,
            notificationId = COUNTDOWN_NOTIFICATION_ID,
            dismissAlarmRequestCode = DISMISS_ALARM_REQUEST_CODE,
            countdownAlarmActivityRequestCode = COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
        )
    }

    init {
        notificationUiDelegate.createNotificationChannel()
    }

    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        alarmDelegate.scheduleCountdownAlarm(orderKey, serviceName, triggerTimeMillis)
    }

    fun cancelCountdownAlarm() {
        alarmDelegate.cancelCountdownAlarm()
    }

    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        alarmDelegate.cancelCountdownAlarmForOrder(orderKey)
    }

    fun buildCountdownCompletionNotification(
        orderKey: OrderKey,
        serviceName: String
    ): android.app.Notification {
        return notificationUiDelegate.buildCountdownCompletionNotification(orderKey, serviceName)
    }

    fun cancelCountdownCompletionNotification() {
        notificationUiDelegate.cancelCountdownCompletionNotification()
    }

    fun canScheduleExactAlarms(): Boolean {
        return alarmDelegate.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(): Boolean {
        return notificationUiDelegate.canUseFullScreenIntent()
    }

    fun getFullScreenIntentPermissionStatus(): FullScreenIntentStatus {
        return when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> {
                FullScreenIntentStatus.GRANTED_BY_DEFAULT
            }
            canUseFullScreenIntent() -> {
                FullScreenIntentStatus.GRANTED
            }
            else -> {
                FullScreenIntentStatus.DENIED
            }
        }
    }

    enum class FullScreenIntentStatus {
        GRANTED_BY_DEFAULT,
        GRANTED,
        DENIED
    }
}
