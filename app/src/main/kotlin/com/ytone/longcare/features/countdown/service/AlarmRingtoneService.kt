package com.ytone.longcare.features.countdown.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI

/**
 * 闹铃响铃服务
 * 负责持续播放闹铃声音和震动，直到用户手动关闭
 */
class AlarmRingtoneService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isPlaying = false

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
            context.startService(intent)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logI("AlarmRingtoneService: 收到启动命令")
        
        if (!isPlaying) {
            startRingtoneAndVibration()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 开始播放闹铃和震动
     */
    private fun startRingtoneAndVibration() {
        try {
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
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
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
        logI("AlarmRingtoneService: 服务销毁")
    }
}
