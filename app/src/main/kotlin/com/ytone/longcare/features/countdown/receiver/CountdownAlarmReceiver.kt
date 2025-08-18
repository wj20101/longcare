package com.ytone.longcare.features.countdown.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 倒计时闹钟广播接收器
 * 处理倒计时完成时的通知、响铃和震动
 */
@AndroidEntryPoint
class CountdownAlarmReceiver : BroadcastReceiver() {

    @Inject
    lateinit var countdownNotificationManager: CountdownNotificationManager

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onReceive(context: Context, intent: Intent) {
        logI("收到倒计时闹钟广播")
        
        val orderId = intent.getLongExtra(CountdownNotificationManager.EXTRA_ORDER_ID, -1L)
        val serviceName = intent.getStringExtra(CountdownNotificationManager.EXTRA_SERVICE_NAME) ?: "未知服务"
        
        if (orderId == -1L) {
            logE("倒计时闹钟广播缺少orderId")
            return
        }

        // 获取WakeLock确保设备唤醒
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "LongCare:CountdownAlarm"
        )
        
        try {
            wakeLock.acquire(30000) // 最多持有30秒
            
            // 显示通知
            countdownNotificationManager.showCountdownCompletionNotification(orderId, serviceName)
            
            // 播放提示音和震动
            receiverScope.launch {
                playAlarmSound(context)
                vibrateDevice(context)
            }
            
            logI("倒计时完成处理完毕: orderId=$orderId, serviceName=$serviceName")
        } catch (e: Exception) {
            logE("处理倒计时闹钟失败: ${e.message}")
        } finally {
            if (wakeLock.isHeld) {
                wakeLock.release()
            }
        }
    }

    /**
     * 播放提示音
     */
    private suspend fun playAlarmSound(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // 检查是否静音模式
            if (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT) {
                logI("设备处于静音模式，跳过播放提示音")
                return
            }

            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build()
                )
                
                // 尝试使用系统默认闹钟铃声
                val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                
                if (alarmUri != null) {
                    setDataSource(context, alarmUri)
                } else {
                    // 如果没有系统铃声，跳过播放
                    logI("无法获取系统铃声，跳过播放提示音")
                    release()
                    return
                }
                
                isLooping = false
                setVolume(0.8f, 0.8f)
            }

            mediaPlayer.setOnPreparedListener { player ->
                player.start()
                logI("倒计时提示音开始播放")
            }
            
            mediaPlayer.setOnCompletionListener { player ->
                player.release()
                logI("倒计时提示音播放完成")
            }
            
            mediaPlayer.setOnErrorListener { player, what, extra ->
                logE("播放倒计时提示音失败: what=$what, extra=$extra")
                player.release()
                true
            }
            
            mediaPlayer.prepareAsync()
            
            // 最多播放10秒后自动停止
            delay(10000)
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
                mediaPlayer.release()
            }
            
        } catch (e: Exception) {
            logE("播放倒计时提示音异常: ${e.message}")
        }
    }

    /**
     * 震动设备
     */
    private fun vibrateDevice(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (!vibrator.hasVibrator()) {
                logI("设备不支持震动")
                return
            }

            // 创建震动模式：短震-停顿-长震-停顿-短震
            val pattern = longArrayOf(0, 200, 100, 500, 100, 200)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(pattern, -1)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, -1)
            }
            
            logI("倒计时完成震动已触发")
        } catch (e: Exception) {
            logE("震动设备失败: ${e.message}")
        }
    }
}