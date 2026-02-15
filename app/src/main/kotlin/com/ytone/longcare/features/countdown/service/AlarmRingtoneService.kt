package com.ytone.longcare.features.countdown.service

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.countdown.tracker.CountdownEventTracker
import com.ytone.longcare.presentation.countdown.CountdownAlarmActivity
import com.ytone.longcare.api.request.OrderInfoRequestModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * 闹铃响铃服务
 * 负责持续播放闹铃声音和震动，直到用户手动关闭
 * 升级为前台服务以确保在后台/锁屏时的优先级
 */
@AndroidEntryPoint
class AlarmRingtoneService : Service() {

    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    companion object {
        private const val EXTRA_REQUEST = "extra_request"
        private const val EXTRA_SERVICE_NAME = "extra_service_name"

        // 通知ID，与CountdownNotificationManager中保持一致
        private const val NOTIFICATION_ID = 2001
        
        /**
         * 启动响铃服务
         */
        fun startRingtone(context: Context, request: OrderInfoRequestModel, serviceName: String) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                putExtra(EXTRA_REQUEST, request)
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
        // 使用 PARTIAL_WAKE_LOCK 保持CPU运行，屏幕点亮由 Activity 的 setTurnScreenOn 处理
        val powerManager = getSystemService<PowerManager>()
        wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LongCare:AlarmRingtoneService"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("AlarmRingtoneService: 收到启动命令")
        
        // 获取WakeLock，保持屏幕常亮
        wakeLock?.acquire(10 * 60 * 1000L /* 10 minutes */)
        
        
        val request =
            intent?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    EXTRA_REQUEST,
                    OrderInfoRequestModel::class.java
                )
            } ?: OrderInfoRequestModel(orderId = -1L, planId = 0)
        
        val serviceName = intent?.getStringExtra(EXTRA_SERVICE_NAME) ?: "未知服务"
        
        // 追踪响铃服务启动事件
        CountdownEventTracker.trackEvent(
            eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_START,
            orderId = request.orderId,
            extras = mapOf("serviceName" to serviceName)
        )

        // 立即升级为前台服务，显示高优先级通知
        startForegroundWithNotification(request, serviceName)
        
        // 启动响铃和震动
        if (!isPlaying) {
            startRingtoneAndVibration()
        }
        
        // 尝试从前台服务启动Activity (作为fullScreenIntent的补充)
        // 注意：Android 10+ (API 29) 限制了后台启动Activity，必须申请 SYSTEM_ALERT_WINDOW 权限或满足特定条件
        // 前台服务属于"可见应用"，通常允许启动Activity，但在某些ROM上可能仍受限
        // 我们在startForegroundWithNotification中已经设置了fullScreenIntent，这是官方推荐的做法
        tryStartActivity(request, serviceName)
        
        return START_STICKY
    }
    
    /**
     * 启动前台服务通知
     */
    private fun startForegroundWithNotification(request: OrderInfoRequestModel, serviceName: String) {
        try {
            val notification = countdownNotificationManager.buildCountdownCompletionNotification(
                request,
                serviceName
            )
            
            // 检查通知权限（使用 NotificationManagerCompat，自动处理版本兼容）
            val notificationManagerCompat = NotificationManagerCompat.from(this)
            val hasNotificationPermission = notificationManagerCompat.areNotificationsEnabled()
            
            logI("AlarmRingtoneService: 通知权限状态=$hasNotificationPermission")
            
            // 确定前台服务类型（Android 10+ 需要指定）
            val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
            
            logI("AlarmRingtoneService: 前台服务类型=$foregroundServiceType, SDK=${Build.VERSION.SDK_INT}")
            
            // 使用Compat库启动前台服务
            try {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    foregroundServiceType
                )
                logI("AlarmRingtoneService: ✅ 前台服务启动成功 (ID=$NOTIFICATION_ID)")
            } catch (e: Exception) {
                logE("AlarmRingtoneService: ❌ ServiceCompat.startForeground失败: ${e.message}")
            }
            
            // 强制刷新通知，确保它显示出来（使用 NotificationManagerCompat）
            // 使用 try-catch 处理可能的 SecurityException
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // Android 13+ 需要检查 POST_NOTIFICATIONS 权限
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManagerCompat.notify(NOTIFICATION_ID, notification)
                    }
                } else {
                    // Android 13 以下不需要运行时权限
                    notificationManagerCompat.notify(NOTIFICATION_ID, notification)
                }
            } catch (e: SecurityException) {
                logE("AlarmRingtoneService: 通知权限被拒绝 - ${e.message}")
            }
            
            logI("AlarmRingtoneService: ✅ 已升级为前台服务并刷新通知 (ID=$NOTIFICATION_ID)")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: ❌ 启动前台服务失败 - ${e.message}", throwable = e)
            
            // 追踪响铃服务错误事件
            CountdownEventTracker.trackError(
                eventType = CountdownEventTracker.EventType.RINGTONE_SERVICE_ERROR,
                orderId = request.orderId,
                throwable = e,
                extras = mapOf("serviceName" to serviceName, "stage" to "startForeground")
            )
        }
    }
    
    /**
     * 尝试启动Activity
     * 
     * Android 14+ 对后台启动Activity有严格限制，即使是前台服务也需要满足特定条件：
     * 1. 使用 fullScreenIntent 通知（推荐方式）
     * 2. 应用具有 SYSTEM_ALERT_WINDOW 权限
     * 3. 应用是设备所有者或配置文件所有者
     * 
     * 我们主要依赖 fullScreenIntent，这里的直接启动作为补充尝试
     */
    private fun tryStartActivity(request: OrderInfoRequestModel, serviceName: String) {
        try {
            logI("AlarmRingtoneService: 尝试启动全屏 Activity (SDK=${Build.VERSION.SDK_INT})")
            
            // Android 14+ 后台启动Activity受限，主要依赖fullScreenIntent
            // 这里仅作为补充尝试，失败是正常的
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                logI("AlarmRingtoneService: Android 14+，跳过直接启动Activity，依赖fullScreenIntent")
                return
            }
            


            val alarmIntent = CountdownAlarmActivity.createIntent(
                this, 
                request, 
                serviceName,
                autoCloseEnabled = false
            ).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or 
                       Intent.FLAG_ACTIVITY_CLEAR_TOP or
                       Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                       Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            
            // 针对 Android 10-13 的处理
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            try {
                pendingIntent.send()
                logI("AlarmRingtoneService: ✅ 通过 PendingIntent 启动 Activity 成功")
            } catch (e: Exception) {
                logE("AlarmRingtoneService: PendingIntent 启动失败，尝试直接 startActivity: ${e.message}")
                startActivity(alarmIntent)
                logI("AlarmRingtoneService: ✅ 通过 startActivity 启动 Activity 成功")
            }
            
        } catch (e: Exception) {
            logI("AlarmRingtoneService: ⚠️ 直接启动Activity失败 (Android 10+正常现象，依赖fullScreenIntent) - ${e.message}")
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
     * 使用兼容方式获取Vibrator实例
     */
    private fun initializeVibrator() {
        try {
            // 使用 ContextCompat 获取 Vibrator（兼容所有版本）
            vibrator = getSystemService<Vibrator>()
            
            // 震动模式：等待0ms -> 震动1000ms -> 暂停500ms -> 循环
            val vibrationPattern = longArrayOf(0, 1000, 500)

            // 根据 API 版本选择震动方式
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8.0+ 使用 VibrationEffect
                val vibrationEffect = VibrationEffect.createWaveform(
                    vibrationPattern,
                    0 // 从索引0开始循环
                )
                vibrator?.vibrate(vibrationEffect)
            } else {
                // Android 8.0 以下使用旧 API
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
