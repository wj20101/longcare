package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 倒计时闹钟广播接收器
 * 处理倒计时完成时的通知、响铃和震动
 */
@AndroidEntryPoint
class CountdownAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        logI("收到倒计时闹钟广播")
        
        val orderId = intent.getLongExtra(CountdownNotificationManager.EXTRA_ORDER_ID, -1L)
        val serviceName = intent.getStringExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME) ?: "未知服务"
        
        if (orderId == -1L) {
            logE("倒计时闹钟广播缺少orderId")
            return
        }

        // 获取WakeLock确保设备唤醒
        val powerManager = context.getSystemService<PowerManager>()
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LongCare:CountdownAlarm"
        )
        
        try {
            wakeLock?.acquire(10000) // 持有10秒，足够启动Activity
            
            // 先停止前台服务，清除进行中的通知
            CountdownForegroundService.stopCountdown(context)
            
            // 启动响铃服务（持续播放声音和震动）
            AlarmRingtoneService.startRingtone(context, orderId.toString(), serviceName)
            
            // 显示完成通知（带关闭按钮）
            countdownNotificationManager.showCountdownCompletionNotification(orderId, serviceName)
            
            // 启动全屏响铃Activity（禁用自动关闭，必须手动点击关闭）
            val alarmIntent = CountdownAlarmActivity.createIntent(
                context, 
                orderId.toString(), 
                serviceName,
                autoCloseEnabled = false // 禁用自动关闭
            )
            context.startActivity(alarmIntent)
            
            logI("倒计时完成处理完毕: orderId=$orderId, serviceName=$serviceName")
        } catch (e: Exception) {
            logE("处理倒计时闹钟失败: ${e.message}")
        } finally {
            if (wakeLock?.isHeld == true) {
                wakeLock.release()
            }
        }
    }
}