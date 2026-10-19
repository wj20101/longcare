package com.ytone.longcare.features.countdown.manager

import android.app.AlarmManager
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.R
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.core.content.edit
import java.util.concurrent.atomic.AtomicBoolean
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 在每个异步组件之间携带原始提醒身份，禁止从当前排程补齐旧请求。 */
internal data class CountdownAlarm(
    val orderKey: OrderKey,
    val serviceName: String,
    val owner: String,
    val triggerTimeMillis: Long,
)

/**
 * 倒计时通知管理器
 * 负责管理倒计时完成时的通知和AlarmManager
 */
@Singleton
class CountdownNotificationManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager,
    private val userSessionRepository: UserSessionRepository,
    private val recoveryStore: LocationRecoveryStore,
    private val orderRepository: OrderRepository,
    private val serviceOrderLifecycle: ServiceOrderLifecycle,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
) {

    companion object {
        internal const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel_v2"
        internal const val COUNTDOWN_NOTIFICATION_ID = 2001
        private const val COUNTDOWN_ALARM_REQUEST_CODE = 3001
        private const val DISMISS_ALARM_REQUEST_CODE = 3002
        private const val COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE = 3003

        const val EXTRA_ORDER_KEY = "extra_order_key"
        private const val EXTRA_ORDER_KEY_LEGACY = "extra_request"
        const val EXTRA_SERVICE_NAME = "extra_service_name"
        private const val EXTRA_SERVICE_NAME_LEGACY = "service_name"

        private const val PREFS_NAME = "countdown_notification_prefs"
        internal const val EXTRA_ALARM_OWNER = "extra_countdown_owner"
        internal const val EXTRA_TRIGGER_TIME = "extra_countdown_trigger_time"
        private const val KEY_TRIGGER_TIME = "trigger_time"
        private const val KEY_ALARM_OWNER = "alarm_owner"
        private const val KEY_ACTIVE_PLAN_ID = "active_plan_id"
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
            actionCountdownAlarmPrefix = ACTION_COUNTDOWN_ALARM_PREFIX,
            keyLastScheduledOrderId = KEY_LAST_SCHEDULED_ORDER_ID,
            noOrderId = NO_ORDER_ID,
        )
    }

    private val notificationUiDelegate by lazy {
        CountdownNotificationUiDelegate(
            context = context,
            channelId = COUNTDOWN_NOTIFICATION_CHANNEL_ID,
            dismissAlarmRequestCode = DISMISS_ALARM_REQUEST_CODE,
            countdownAlarmActivityRequestCode = COUNTDOWN_ALARM_ACTIVITY_REQUEST_CODE,
        )
    }

    init {
        notificationUiDelegate.createNotificationChannel()
    }

    private val observingSession = AtomicBoolean(false)

    /** 一份排程绑定一位登录用户；读取磁盘期间不清理，退出或换号统一释放。 */
    fun startSessionObservation() {
        if (!observingSession.compareAndSet(false, true)) return
        applicationScope.launch(Dispatchers.Main.immediate) {
            combine(userSessionRepository.sessionState, serviceOrderLifecycle.orderState) { _, _ ->
                Unit
            }.collect {
                onSessionChanged()
            }
        }
    }

    /** 冷进程广播最多等待五秒，不把磁盘读取中的 Unknown 当作退出登录。 */
    suspend fun awaitResolvedSession(): Boolean = withTimeoutOrNull(5_000L) {
        userSessionRepository.sessionState.first { it !is SessionState.Unknown } is SessionState.LoggedIn
    } ?: false

    @Synchronized
    private fun onSessionChanged() {
        // 观察只触发重查；排队旧发射不能取消其后建立的新排程。
        val session = userSessionRepository.sessionState.value
        if (session is SessionState.Unknown) return
        val current = currentOwner(session)
        val stored = prefs.getString(KEY_ALARM_OWNER, null)
        if (current == null || (stored != null && stored != current)) clearAlarm()
        // 仅比较当前登录与持久排程的身份，避免迟到的账号观察结果清除新排程。
        // 无 owner 的旧排程到点后凭已有恢复记录和服务端状态验证归属。
        val orderState = serviceOrderLifecycle.orderState.value
        if (orderState != null && orderState.orderId == activeOrderKey()?.orderId && !orderState.isInProgress()) {
            clearAlarm()
        }
    }

    /** 整个冷启动/旧排程验证共用五秒预算，晚到结果不写入或启动新提醒。 */
    suspend fun acceptAlarm(intent: Intent): Boolean = try {
        withTimeoutOrNull(5_000L) {
            if (!awaitResolvedSession()) return@withTimeoutOrNull false
            if (ownsAlarm(intent)) return@withTimeoutOrNull true
            if (intent.hasExtra(EXTRA_ALARM_OWNER)) return@withTimeoutOrNull false
            val orderKey = extractOrderKey(intent)
            val owner = currentOwner() ?: return@withTimeoutOrNull false
            if (orderKey.orderId <= 0 || prefs.getString(KEY_ALARM_OWNER, null) != null ||
                prefs.getLong(KEY_LAST_SCHEDULED_ORDER_ID, NO_ORDER_ID) != orderKey.orderId) {
                return@withTimeoutOrNull false
            }
            val record = recoveryStore.read() ?: return@withTimeoutOrNull false
            val account = record.account
            if (record.orderId != orderKey.orderId ||
                "${account.companyId}:${account.accountId}:${account.userId}" != owner) return@withTimeoutOrNull false
            val state = (orderRepository.getOrderState(orderKey.orderId) as? ApiResult.Success)?.data
            if (state == null || state.orderId != orderKey.orderId || !state.isInProgress()) return@withTimeoutOrNull false
            currentCoroutineContext().ensureActive()
            synchronized(this@CountdownNotificationManager) {
                if (currentOwner() != owner || prefs.getString(KEY_ALARM_OWNER, null) != null ||
                    prefs.getLong(KEY_LAST_SCHEDULED_ORDER_ID, NO_ORDER_ID) != orderKey.orderId) {
                    return@synchronized false
                }
                val triggerTime = System.currentTimeMillis()
                prefs.edit {
                    putString(KEY_ALARM_OWNER, owner)
                    putInt(KEY_ACTIVE_PLAN_ID, orderKey.planId)
                    putLong(KEY_TRIGGER_TIME, triggerTime)
                }
                intent.putExtra(EXTRA_ALARM_OWNER, owner).putExtra(EXTRA_TRIGGER_TIME, triggerTime)
                true
            }
        } ?: false
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        CountdownEventTracker.trackError(CountdownEventTracker.EventType.ALARM_RECEIVER_ERROR,
            extractOrderKey(intent).orderId, error, mapOf("stage" to "legacy_owner_validation"))
        false
    }

    @Synchronized
    fun scheduleCountdownAlarm(
        orderKey: OrderKey,
        serviceName: String,
        triggerTimeMillis: Long
    ) {
        val owner = currentOwner() ?: return
        val state = serviceOrderLifecycle.orderState.value
        if (state?.orderId == orderKey.orderId && !state.isInProgress()) {
            if (activeOrderKey() == orderKey) clearAlarm()
            return
        }
        if (prefs.getString(KEY_ALARM_OWNER, null) != owner || activeOrderKey() != orderKey ||
            prefs.getLong(KEY_TRIGGER_TIME, 0L) != triggerTimeMillis) {
            clearAlarm()
        }
        prefs.edit {
            putString(KEY_ALARM_OWNER, owner)
            putLong(KEY_LAST_SCHEDULED_ORDER_ID, orderKey.orderId)
            putInt(KEY_ACTIVE_PLAN_ID, orderKey.planId)
            putLong(KEY_TRIGGER_TIME, triggerTimeMillis)
        }
        if (!alarmDelegate.scheduleCountdownAlarm(orderKey, serviceName, triggerTimeMillis, owner)) {
            clearAlarm()
        }
    }

    @Synchronized
    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) {
        if (activeOrderKey() == orderKey) clearAlarm()
    }

    @Synchronized
    fun ownsAlarm(orderKey: OrderKey, owner: String?, triggerTimeMillis: Long): Boolean {
        // 终态发布可能早于 Main 上的清理观察回调；交付边界直接读取已确认状态。
        val state = serviceOrderLifecycle.orderState.value
        val orderStillActive = state == null || state.orderId != orderKey.orderId || state.isInProgress()
        return orderStillActive && owner != null && owner == currentOwner() && owner == prefs.getString(KEY_ALARM_OWNER, null) &&
            activeOrderKey() == orderKey && triggerTimeMillis > 0 &&
            prefs.getLong(KEY_TRIGGER_TIME, 0L) == triggerTimeMillis
    }

    fun ownsAlarm(intent: Intent?): Boolean = ownsAlarm(extractOrderKey(intent),
        intent?.getStringExtra(EXTRA_ALARM_OWNER), intent?.getLongExtra(EXTRA_TRIGGER_TIME, 0L) ?: 0L)

    @Synchronized
    fun dismissAlarm(orderKey: OrderKey, owner: String?, triggerTimeMillis: Long) {
        if (ownsAlarm(orderKey, owner, triggerTimeMillis)) clearAlarm()
    }

    private fun activeOrderKey(): OrderKey? {
        if (!prefs.contains(KEY_LAST_SCHEDULED_ORDER_ID)) return null
        return OrderKey(prefs.getLong(KEY_LAST_SCHEDULED_ORDER_ID, NO_ORDER_ID), prefs.getInt(KEY_ACTIVE_PLAN_ID, 0))
    }

    private fun currentOwner(session: SessionState = userSessionRepository.sessionState.value): String? = session.user?.let {
        "${it.companyId}:${it.accountId}:${it.userId}"
    }

    private fun clearAlarm() {
        val orderKey = activeOrderKey()
        val owner = prefs.getString(KEY_ALARM_OWNER, null)
        if (orderKey == null && owner == null) return
        val triggerTime = prefs.getLong(KEY_TRIGGER_TIME, 0L)
        alarmDelegate.cancelCountdownAlarm()
        prefs.edit {
            remove(KEY_ALARM_OWNER)
            remove(KEY_LAST_SCHEDULED_ORDER_ID)
            remove(KEY_ACTIVE_PLAN_ID)
            remove(KEY_TRIGGER_TIME)
        }
        AlarmRingtoneService.stopRingtone(context)
        notificationManager.cancel(COUNTDOWN_NOTIFICATION_ID)
        // 仅关闭正在展示的同一提醒，旧页面的关闭动作无法影响后来的订单。
        context.sendBroadcast(Intent(DismissAlarmReceiver.ACTION_STOP_ALARM).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_ORDER_KEY, orderKey)
            putExtra(EXTRA_ALARM_OWNER, owner)
            putExtra(EXTRA_TRIGGER_TIME, triggerTime)
        })
    }

    /** Receiver 只负责交付；旧版本排程的接管仍由 acceptAlarm 验证。 */
    internal suspend fun onAlarmTriggered(intent: Intent) {
        if (!acceptAlarm(intent)) return
        val alarm = extractAlarm(intent) ?: return
        startAlarm(alarm)
    }

    @Synchronized
    private fun startAlarm(alarm: CountdownAlarm) {
        if (!ownsAlarm(alarm)) return
        CountdownEventTracker.trackEvent(CountdownEventTracker.EventType.ALARM_TRIGGERED,
            alarm.orderKey.orderId, mapOf("triggerTime" to System.currentTimeMillis()))
        try {
            AlarmRingtoneService.startRingtone(context, alarm)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trackPresentationFailure(alarm, error, "start_service")
            // 后台 FGS 被拒绝时只发布系统通知，不另开普通服务或强拉 Activity。
            publishAlarmNotification(alarm)
        }
    }

    internal fun extractAlarm(intent: Intent?): CountdownAlarm? {
        val orderKey = extractOrderKey(intent)
        val owner = intent?.getStringExtra(EXTRA_ALARM_OWNER) ?: return null
        val triggerTime = intent.getLongExtra(EXTRA_TRIGGER_TIME, 0L)
        if (orderKey.orderId <= 0L || triggerTime <= 0L) return null
        return CountdownAlarm(orderKey,
            extractServiceName(intent, context.getString(R.string.countdown_alarm_default_service)),
            owner, triggerTime)
    }

    internal fun ownsAlarm(alarm: CountdownAlarm): Boolean =
        ownsAlarm(alarm.orderKey, alarm.owner, alarm.triggerTimeMillis)

    /** 校验与发布共用撤销排程的锁；不会把旧请求套上当前排程的新身份。 */
    @Synchronized
    internal fun startAlarmForeground(service: Service, alarm: CountdownAlarm): Boolean {
        if (!ownsAlarm(alarm)) return false
        return try {
            val notification = buildAlarmNotification(alarm)
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else 0
            // POST_NOTIFICATIONS 被拒绝仍可启动 FGS；系统负责其受限展示。
            if (!ownsAlarm(alarm)) return false
            ServiceCompat.startForeground(service, COUNTDOWN_NOTIFICATION_ID, notification, type)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trackPresentationFailure(alarm, error, "start_foreground")
            publishAlarmNotification(alarm)
            false
        }
    }

    @Synchronized
    private fun publishAlarmNotification(alarm: CountdownAlarm): Boolean {
        if (!ownsAlarm(alarm)) return false
        return try {
            if (!notificationsEnabled()) return false
            val notification = buildAlarmNotification(alarm)
            // SessionState 可能先于其清理观察回调变更，发布前仍复验原始身份。
            if (!ownsAlarm(alarm)) return false
            notificationManager.notify(COUNTDOWN_NOTIFICATION_ID, notification)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            trackPresentationFailure(alarm, error, "publish_notification")
            false
        }
    }

    private fun notificationsEnabled(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    private fun buildAlarmNotification(alarm: CountdownAlarm): Notification {
        CountdownEventTracker.trackEvent(CountdownEventTracker.EventType.NOTIFICATION_BUILD,
            alarm.orderKey.orderId, mapOf(
                "fullScreenAllowed" to canUseFullScreenIntent(),
                "notificationsEnabled" to notificationsEnabled(),
                "channelImportance" to NotificationManagerCompat.from(context)
                    .getNotificationChannelCompat(COUNTDOWN_NOTIFICATION_CHANNEL_ID)?.importance,
                "sdk" to Build.VERSION.SDK_INT,
            ))
        return notificationUiDelegate.buildCountdownCompletionNotification(
            alarm.orderKey, alarm.serviceName, alarm.owner, alarm.triggerTimeMillis)
    }

    private fun trackPresentationFailure(alarm: CountdownAlarm, error: Exception, stage: String) {
        logE("倒计时提醒失败: stage=$stage, orderId=${alarm.orderKey.orderId}", throwable = error)
        CountdownEventTracker.trackError(CountdownEventTracker.EventType.RINGTONE_SERVICE_ERROR,
            alarm.orderKey.orderId, error, mapOf("stage" to stage))
    }

    fun canScheduleExactAlarms(): Boolean {
        return alarmDelegate.canScheduleExactAlarms()
    }

    fun canUseFullScreenIntent(): Boolean {
        return notificationUiDelegate.canUseFullScreenIntent()
    }

}
