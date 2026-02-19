package com.ytone.longcare.presentation.countdown

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.receiver.DismissAlarmReceiver
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CountdownAlarmActivity : AppCompatActivity() {
    
    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager
    
    private var autoCloseJob: Job? = null
    private var stopAlarmReceiverRegistered = false
    private var alarmCleanedUp = false
    
    companion object {
        private const val TAG = "CountdownAlarmActivity"
        private const val EXTRA_AUTO_CLOSE_ENABLED = "auto_close_enabled"
        private const val AUTO_CLOSE_DELAY_MS = 30000L // 30秒
        
        fun createIntent(context: Context, orderKey: OrderKey, serviceName: String, autoCloseEnabled: Boolean = true): Intent {
            return Intent(context, CountdownAlarmActivity::class.java).apply {
                putExtra(CountdownNotificationManager.EXTRA_ORDER_KEY, orderKey)
                putExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME, serviceName)
                putExtra(EXTRA_AUTO_CLOSE_ENABLED, autoCloseEnabled)
                // 确保Activity可以从后台启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
    }
    
    private val stopAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            stopAlarmAndFinish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        logI("========================================")
        logI("✅ CountdownAlarmActivity onCreate 被调用")
        logI("========================================")
        
        // 设置锁屏显示和点亮屏幕
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            // Android 8.1+ (API 27+) 使用 Activity 的方法
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            // 旧版本使用 WindowManager flags
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        logI("使用 setShowWhenLocked/setTurnScreenOn 设置锁屏显示")
        
        // 设置Window flags保持屏幕常亮
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        // 使用 WindowCompat 设置全屏显示
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        logI("Window flags 已设置")
        
        val orderKey = CountdownNotificationManager.extractOrderKey(intent)
        
        val orderId = orderKey.orderId.toString()
        val serviceName = CountdownNotificationManager.extractServiceName(intent, "护理服务")
        val autoCloseEnabled = intent.getBooleanExtra(EXTRA_AUTO_CLOSE_ENABLED, true)
        
        // 注册停止响铃广播接收器
        val filter = IntentFilter(DismissAlarmReceiver.ACTION_STOP_ALARM)
        ContextCompat.registerReceiver(this, stopAlarmReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        stopAlarmReceiverRegistered = true
        
        // 如果启用自动关闭，设置30秒后自动关闭
        if (autoCloseEnabled) {
            setupAutoClose()
        }
        
        setContent {
            LongCareTheme {
                CountdownAlarmScreen(
                    orderId = orderId,
                    serviceName = serviceName,
                    onDismiss = {
                        stopAlarmAndFinish()
                    }
                )
            }
        }

        // 禁用返回键，强制用户点击关闭按钮
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // 拦截返回键，不做任何操作
            }
        })
    }
    
    private fun setupAutoClose() {
        cancelAutoClose()
        autoCloseJob = lifecycleScope.launch {
            delay(AUTO_CLOSE_DELAY_MS)
            stopAlarmAndFinish()
        }
    }
    
    private fun cancelAutoClose() {
        autoCloseJob?.cancel()
        autoCloseJob = null
    }
    
    private fun stopAlarmAndFinish() {
        cleanupAlarmState()
        if (!isFinishing) {
            finish()
        }
    }

    private fun cleanupAlarmState() {
        if (alarmCleanedUp) {
            return
        }
        alarmCleanedUp = true

        // 取消自动关闭
        cancelAutoClose()

        // 停止响铃服务并清理通知
        AlarmRingtoneService.stopRingtone(this)
        countdownNotificationManager.cancelCountdownCompletionNotification()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (stopAlarmReceiverRegistered) {
            unregisterReceiver(stopAlarmReceiver)
            stopAlarmReceiverRegistered = false
        }
        cleanupAlarmState()
    }
}
