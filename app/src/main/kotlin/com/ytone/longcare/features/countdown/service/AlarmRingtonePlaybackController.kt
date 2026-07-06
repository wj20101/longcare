package com.ytone.longcare.features.countdown.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.content.getSystemService
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI

internal class AlarmRingtonePlaybackController(
    private val context: Context
) {
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    var isPlaying: Boolean = false
        private set

    fun start(): Boolean {
        return try {
            logI("AlarmRingtoneService: 准备启动响铃和震动")
            initializeMediaPlayer()
            initializeVibrator()
            isPlaying = true
            logI("AlarmRingtoneService: 闹铃和震动已启动")
            true
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 启动闹铃失败 - ${e.message}")
            false
        }
    }

    fun stop() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            } catch (e: Exception) {
                logE("AlarmRingtoneService: 停止MediaPlayer失败 - ${e.message}")
            } finally {
                mediaPlayer = null
            }
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 停止Vibrator失败 - ${e.message}")
        } finally {
            vibrator = null
        }

        isPlaying = false
        logI("AlarmRingtoneService: 闹铃和震动已停止")
    }

    private fun initializeMediaPlayer() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                setVolume(1.0f, 1.0f)
                prepare()
                start()
            }

            logI("AlarmRingtoneService: MediaPlayer已启动")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 初始化MediaPlayer失败 - ${e.message}")
            throw e
        }
    }

    private fun initializeVibrator() {
        try {
            vibrator = context.getSystemService<Vibrator>()
            val vibrationPattern = longArrayOf(0, 1000, 500)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createWaveform(vibrationPattern, 0)
                vibrator?.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(vibrationPattern, 0)
            }

            logI("AlarmRingtoneService: Vibrator已启动")
        } catch (e: Exception) {
            logE("AlarmRingtoneService: 初始化Vibrator失败 - ${e.message}")
        }
    }
}
