package com.ytone.longcare.features.countdown.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.di.ServiceCountdownEntryPoint
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import dagger.hilt.android.EntryPointAccessors

/**
 * 闹铃响铃服务
 * 负责持续播放闹铃声音和震动，直到用户手动关闭
 * 升级为前台服务以确保在后台/锁屏时的优先级
 */
class AlarmRingtoneService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    // 通知ID，与CountdownNotificationManager中保持一致
    private val NOTIFICATION_ID = 2001

    companion object {
        private const val EXTRA_ORDER_ID = "extra_order_id"
        private const val EXTRA_SERVICE_NAME = "extra_service_name"
        
        /**
         * 启动响铃服务
         */
        fun startRingtone(context: Context, orderId: String, serviceName: String) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                putExtra(EXTRA_ORDER_ID, orderId)
                putExtra(EXTRA_SERVICE_NAME, serviceName)
            }
            // 使用Compat库确保兼容性，自动处理Android 8.0+的前台服务启动
            ContextCompat.startForegroundService(context, intent)
        }
        
        /**
         * 停止响铃服务
         */
        fun stopRingtone(context: Context) {
            val intent = Intent(context, AlarmRingtoneService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        logI("AlarmRingtoneService: 服务创建")
        
        // 初始化WakeLock
        val powerManager = getSystemService<PowerManager>()
        wakeLock = powerManager?.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or
            PowerManager.ACQUIRE_CAUSES_WAKEUP or
            PowerManager.ON_AFTER_RELEASE,
            "LongCare:AlarmRingtoneService"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("AlarmRingtoneService: 收到启动命令")
        
        // 获取WakeLock，保持屏幕常亮
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
        
        val orderId = intent?.getStringExtra(EXTRA_ORDER_ID) ?: ""
        val serviceName = intent?.getStringExtra(EXTRA_SERVICE_NAME) ?: "未知服务"
        
        // 立即升级为前台服务，显示高优先级通知
        startForegroundWithNotification(orderId, serviceName)
        
        // 启动响铃和震动
        if (!isPlaying) {
            startRingtoneAndVibration()
        }
        
        // 尝试从前台服务启动Activity (作为fullScreenIntent的补充)
        // 注意：Android 10+ (API 29) 限制了后台启动Activity，必须申请 SYSTEM_ALERT_WINDOW 权限或满足特定条件
        // 前台服务属于"可见应用"，通常允许启动Activity，但在某些ROM上可能仍受限
        // 我们在startForegroundWithNotification中已经设置了fullScreenIntent，这是官方推荐的做法
        tryStartActivity(orderId, serviceName)
        
        return START_STICKY
    }
    
    /**
     * 启动前台服务通知
     */
    private fun startForegroundWithNotification(orderId: String, serviceName: String) {
        try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ServiceCountdownEntryPoint::class.java
            )
            val manager = entryPoint.countdownNotificationManager()
            
            val notification = manager.buildCountdownCompletionNotification(
                orderId.toLongOrNull() ?: -1L,
                serviceName
            )
            
            // 使用Compat库启动前台服务，自动适配不同版本
            // 注意：这里必须使用 ServiceCompat.startForeground，并且传入正确的 foregroundServiceType
            // 否则在 Android 14+ 上会抛出 SecurityException 或不显示通知
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                } else {
                    0
                }
            )
            
            // 强制刷新通知，确保它显示出来
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.notify(NOTIFICATION_ID, notification)
            
            logI("AlarmRingtoneService: 已升级为前台服务 (ID=$NOTIFICATION_ID)")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 启动前台服务失败 - ${e.message}")
        }
    }
    
    /**
     * 尝试启动Activity
     */
    private fun tryStartActivity(orderId: String, serviceName: String) {
        try {
            logI("AlarmRingtoneService: 尝试启动全屏 Activity")
            val alarmIntent = CountdownAlarmActivity.createIntent(
                this, 
                orderId, 
                serviceName,
                autoCloseEnabled = false
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT // 确保Activity被带到前台
            }
            
            // 针对 Android 10+ 后台启动限制的额外处理
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                alarmIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            
            // 尝试直接发送 PendingIntent，有时比 startActivity 更能绕过限制
            try {
                pendingIntent.send()
                logI("AlarmRingtoneService: 通过 PendingIntent 启动 Activity 成功")
            } catch (e: Exception) {
                logE("AlarmRingtoneService: PendingIntent 启动失败，尝试直接 startActivity")
                startActivity(alarmIntent)
                logI("AlarmRingtoneService: 通过 startActivity 启动 Activity 成功")
            }
            
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 尝试启动Activity失败 (正常现象，如果应用在后台) - ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 开始播放闹铃和震动
     */
    private fun startRingtoneAndVibration() {
        try {
            logI("AlarmRingtoneService: 准备启动响铃和震动")
            
            // 初始化并播放闹铃声音
            initializeMediaPlayer()
            
            // 初始化并开始震动
            initializeVibrator()
            
            isPlaying = true
            logI("AlarmRingtoneService: 闹铃和震动已启动")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 启动闹铃失败 - ${e.message}")
            stopSelf()
        }
    }

    /**
     * 初始化MediaPlayer播放闹铃声音
     */
    private fun initializeMediaPlayer() {
        try {
            // 获取系统闹铃铃声URI
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                
                // 设置音频属性为闹钟类型
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED) // 强制发声
                        .build()
                )
                
                // 设置为循环播放
                isLooping = true
                
                // 设置音量为最大
                setVolume(1.0f, 1.0f)
                
                // 准备并播放
                prepare()
                start()
            }
            
            logI("AlarmRingtoneService: MediaPlayer已启动")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 初始化MediaPlayer失败 - ${e.message}")
            throw e
        }
    }

    /**
     * 初始化Vibrator开始震动
     */
    private fun initializeVibrator() {
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService<VibratorManager>()?.defaultVibrator
            } else {
                getSystemService<Vibrator>()
            }
            
            // 震动模式：等待0ms -> 震动1000ms -> 暂停500ms -> 循环
            val vibrationPattern = longArrayOf(0, 1000, 500)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(
                    vibrationPattern,
                    0 // 从索引0开始循环
                )
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationPattern, 0)
            }
            
            logI("AlarmRingtoneService: Vibrator已启动")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 初始化Vibrator失败 - ${e.message}")
            // 震动失败不影响声音播放，继续执行
        }
    }

    /**
     * 停止播放闹铃和震动
     */
    private fun stopRingtoneAndVibration() {
        try {
            // 停止MediaPlayer
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
                mediaPlayer = null
            }
            
            // 停止震动
            vibrator?.cancel()
            vibrator = null
            
            isPlaying = false
            logI("AlarmRingtoneService: 闹铃和震动已停止")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 停止闹铃失败 - ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtoneAndVibration()
        
        // 释放WakeLock
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        logI("AlarmRingtoneService: 服务销毁")
    }
}
