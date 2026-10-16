package com.ytone.longcare.presentation.countdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.R
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 展示当前提醒；声音与通知的生命周期由 manager / service 持有。 */
@AndroidEntryPoint
class CountdownAlarmActivity : AppCompatActivity() {
    @Inject lateinit var countdownNotificationManager: CountdownNotificationManager

    private data class AlarmContent(val orderKey: OrderKey, val owner: String, val triggerTime: Long, val serviceName: String)
    private var content by mutableStateOf<AlarmContent?>(null)
    private var intentJob: Job? = null
    private var stopAlarmReceiverRegistered = false

    companion object {
        fun createIntent(context: Context, orderKey: OrderKey, serviceName: String, owner: String, triggerTimeMillis: Long): Intent =
            Intent(context, CountdownAlarmActivity::class.java).apply {
                putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
                putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
                putExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER, owner)
                putExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, triggerTimeMillis)
                action = "com.ytone.longcare.COUNTDOWN_SHOW_${orderKey.orderId}_${orderKey.planId}_$triggerTimeMillis"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
    }

    private val stopAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val displayed = content ?: return
            if (CountdownNotificationManager.extractOrderKey(intent) == displayed.orderKey &&
                intent?.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER) == displayed.owner &&
                intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L) == displayed.triggerTime
            ) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ContextCompat.registerReceiver(this, stopAlarmReceiver,
            IntentFilter(DismissAlarmReceiver.ACTION_STOP_ALARM), ContextCompat.RECEIVER_NOT_EXPORTED)
        stopAlarmReceiverRegistered = true
        setContent {
            LongCareTheme {
                content?.let { displayed ->
                    CountdownAlarmScreen(displayed.orderKey.orderId.toString(), displayed.serviceName) {
                        // 捕获本次展示身份，迟到的旧按钮不能关闭刚到达的新订单。
                        if (countdownNotificationManager.ownsAlarm(displayed.orderKey, displayed.owner, displayed.triggerTime)) {
                            countdownNotificationManager.dismissAlarm(displayed.orderKey, displayed.owner, displayed.triggerTime)
                            if (content == displayed) finish()
                        }
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })
        showAlarm(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showAlarm(intent)
    }

    private fun showAlarm(intent: Intent) {
        intentJob?.cancel()
        intentJob = lifecycleScope.launch {
            if (!countdownNotificationManager.awaitResolvedSession() ||
                !countdownNotificationManager.ownsAlarm(intent)
            ) {
                val displayed = content
                if (displayed == null || !countdownNotificationManager.ownsAlarm(
                    displayed.orderKey, displayed.owner, displayed.triggerTime)) finish()
                return@launch
            }
            setIntent(intent)
            content = AlarmContent(
                CountdownNotificationManager.extractOrderKey(intent),
                requireNotNull(intent.getStringExtra(CountdownNotificationManager.EXTRA_ALARM_OWNER)),
                intent.getLongExtra(CountdownNotificationManager.EXTRA_TRIGGER_TIME, 0L),
                CountdownNotificationManager.extractServiceName(intent, getString(R.string.countdown_alarm_default_service)),
            )
        }
    }

    override fun onDestroy() {
        if (stopAlarmReceiverRegistered) unregisterReceiver(stopAlarmReceiver)
        super.onDestroy()
    }
}
