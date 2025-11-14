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
        logI("========================================")
        logI("🔔 收到倒计时闹钟广播")
        logI("========================================")
        
        val orderId = intent.getLongExtra(CountdownNotificationManager.EXTRA_ORDER_ID, -1L)
        val serviceName = intent.getStringExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME) ?: "未知服务"
        
        if (orderId == -1L) {
            logE("❌ 倒计时闹钟广播缺少orderId")
            return
        }

        logI("📋 订单信息: orderId=$orderId, serviceName=$serviceName")

        // 获取WakeLock确保设备唤醒（使用FULL_WAKE_LOCK确保屏幕点亮）
        val powerManager = context.getSystemService<PowerManager>()
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or 
            PowerManager.ACQUIRE_CAUSES_WAKEUP or 
            PowerManager.ON_AFTER_RELEASE,
            "LongCare:CountdownAlarm"
        )
        
        try {
            // 持有30秒WakeLock，确保有足够时间完成所有操作
            wakeLock?.acquire(30000)
            logI("✅ WakeLock已获取")
            
            // 1. 先停止前台服务，清除进行中的通知
            CountdownForegroundService.stopCountdown(context)
            logI("✅ 倒计时前台服务已停止")
            
            // 2. 启动响铃服务（持续播放声音和震动）
            AlarmRingtoneService.startRingtone(context, orderId.toString(), serviceName)
            logI("✅ 响铃服务已启动")
            
            // 3. 显示完成通知（带关闭按钮）
            countdownNotificationManager.showCountdownCompletionNotification(orderId, serviceName)
            logI("✅ 完成通知已显示")
            
            // 4. 启动全屏响铃Activity（禁用自动关闭，必须手动点击关闭）
            val alarmIntent = CountdownAlarmActivity.createIntent(
                context, 
                orderId.toString(), 
                serviceName,
                autoCloseEnabled = false // 禁用自动关闭
            ).apply {
                // 添加必要的标志确保在锁屏状态下也能启动
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_NO_USER_ACTION
            }
            context.startActivity(alarmIntent)
            logI("✅ 全屏响铃Activity已启动")
            
            logI("========================================")
            logI("✅ 倒计时完成处理完毕")
            logI("========================================")
        } catch (e: Exception) {
            logE("========================================")
            logE("❌ 处理倒计时闹钟失败: ${e.message}", throwable = e)
            logE("========================================")
        } finally {
            // 延迟释放WakeLock，确保Activity完全启动
            if (wakeLock?.isHeld == true) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    wakeLock.release()
                    logI("✅ WakeLock已释放")
                }, 5000) // 5秒后释放
            }
        }
    }
}