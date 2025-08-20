package com.ytone.longcare.features.countdown.debug

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.common.utils.logW
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 倒计时通知调试助手
 * 用于排查倒计时通知未出现的问题
 */
@Singleton
class CountdownNotificationDebugHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
    private val alarmManager: AlarmManager
) {

    companion object {
        private const val COUNTDOWN_NOTIFICATION_CHANNEL_ID = "countdown_completion_channel"
    }

    /**
     * 诊断倒计时通知问题
     */
    fun diagnoseNotificationIssues(context: Context) {
        performFullDiagnosis()
    }
    
    /**
     * 执行完整的倒计时通知诊断
     */
    fun performFullDiagnosis(): DiagnosisResult {
        logI("开始倒计时通知诊断...")
        
        val result = DiagnosisResult()
        
        // 检查基础权限
        result.hasVibratePermission = checkVibratePermission()
        result.hasWakeLockPermission = checkWakeLockPermission()
        result.hasPostNotificationPermission = checkPostNotificationPermission()
        
        // 检查精确闹钟权限
        result.canScheduleExactAlarms = checkExactAlarmPermission()
        
        // 检查通知设置
        result.areNotificationsEnabled = checkNotificationsEnabled()
        result.isChannelEnabled = checkNotificationChannel()
        
        // 检查电池优化
        result.isBatteryOptimizationIgnored = checkBatteryOptimization()
        
        // 检查系统设置
        result.isDoNotDisturbActive = checkDoNotDisturbMode()
        
        // 输出诊断结果
        logDiagnosisResult(result)
        
        return result
    }

    /**
     * 检查震动权限
     */
    private fun checkVibratePermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.VIBRATE
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            logI("✓ 震动权限已授予")
        } else {
            logE("✗ 震动权限未授予")
        }
        
        return hasPermission
    }

    /**
     * 检查唤醒锁权限
     */
    private fun checkWakeLockPermission(): Boolean {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, 
            Manifest.permission.WAKE_LOCK
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            logI("✓ 唤醒锁权限已授予")
        } else {
            logE("✗ 唤醒锁权限未授予")
        }
        
        return hasPermission
    }

    /**
     * 检查通知权限（Android 13+）
     */
    private fun checkPostNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, 
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            
            if (hasPermission) {
                logI("✓ 通知权限已授予 (Android 13+)")
            } else {
                logE("✗ 通知权限未授予 (Android 13+)")
            }
            
            return hasPermission
        } else {
            logI("✓ 通知权限检查跳过 (Android < 13)")
            return true
        }
    }

    /**
     * 检查精确闹钟权限（Android 12+）
     */
    private fun checkExactAlarmPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val canSchedule = alarmManager.canScheduleExactAlarms()
            
            if (canSchedule) {
                logI("✓ 精确闹钟权限已授予 (Android 12+)")
            } else {
                logE("✗ 精确闹钟权限未授予 (Android 12+)")
            }
            
            return canSchedule
        } else {
            logI("✓ 精确闹钟权限检查跳过 (Android < 12)")
            return true
        }
    }

    /**
     * 检查应用通知是否启用
     */
    private fun checkNotificationsEnabled(): Boolean {
        val enabled = notificationManager.areNotificationsEnabled()
        
        if (enabled) {
            logI("✓ 应用通知已启用")
        } else {
            logE("✗ 应用通知已禁用")
        }
        
        return enabled
    }

    /**
     * 检查通知渠道状态
     */
    private fun checkNotificationChannel(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = notificationManager.getNotificationChannel(COUNTDOWN_NOTIFICATION_CHANNEL_ID)
            
            if (channel == null) {
                logE("✗ 倒计时通知渠道不存在")
                return false
            }
            
            val importance = channel.importance
            when {
                importance == NotificationManager.IMPORTANCE_NONE -> {
                    logE("✗ 倒计时通知渠道已被禁用")
                    return false
                }
                importance >= NotificationManager.IMPORTANCE_DEFAULT -> {
                    logI("✓ 倒计时通知渠道已启用 (重要性: $importance)")
                    return true
                }
                else -> {
                    logW("⚠ 倒计时通知渠道重要性较低 (重要性: $importance)")
                    return true
                }
            }
        } else {
            logI("✓ 通知渠道检查跳过 (Android < 8.0)")
            return true
        }
    }

    /**
     * 检查电池优化设置
     */
    private fun checkBatteryOptimization(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIgnored = powerManager.isIgnoringBatteryOptimizations(context.packageName)
            
            if (isIgnored) {
                logI("✓ 应用已加入电池优化白名单")
            } else {
                logW("⚠ 应用未加入电池优化白名单，可能影响闹钟触发")
            }
            
            return isIgnored
        } else {
            logI("✓ 电池优化检查跳过 (Android < 6.0)")
            return true
        }
    }

    /**
     * 检查勿扰模式
     */
    private fun checkDoNotDisturbMode(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val currentInterruptionFilter = notificationManager.currentInterruptionFilter
            val isActive = currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
            
            if (isActive) {
                logW("⚠ 勿扰模式已激活 (过滤级别: $currentInterruptionFilter)")
            } else {
                logI("✓ 勿扰模式未激活")
            }
            
            return isActive
        } else {
            logI("✓ 勿扰模式检查跳过 (Android < 6.0)")
            return false
        }
    }

    /**
     * 输出诊断结果
     */
    private fun logDiagnosisResult(result: DiagnosisResult) {
        logI("==================== 诊断结果 ====================")
        logI("震动权限: ${if (result.hasVibratePermission) "✓" else "✗"}")
        logI("唤醒锁权限: ${if (result.hasWakeLockPermission) "✓" else "✗"}")
        logI("通知权限: ${if (result.hasPostNotificationPermission) "✓" else "✗"}")
        logI("精确闹钟权限: ${if (result.canScheduleExactAlarms) "✓" else "✗"}")
        logI("应用通知启用: ${if (result.areNotificationsEnabled) "✓" else "✗"}")
        logI("通知渠道启用: ${if (result.isChannelEnabled) "✓" else "✗"}")
        logI("电池优化白名单: ${if (result.isBatteryOptimizationIgnored) "✓" else "⚠"}")
        logI("勿扰模式: ${if (result.isDoNotDisturbActive) "⚠" else "✓"}")
        
        val hasIssues = !result.hasVibratePermission || 
                       !result.hasWakeLockPermission || 
                       !result.hasPostNotificationPermission || 
                       !result.canScheduleExactAlarms || 
                       !result.areNotificationsEnabled || 
                       !result.isChannelEnabled
        
        if (hasIssues) {
            logE("发现问题！请检查上述标记为 ✗ 的项目")
        } else {
            logI("所有关键检查项都正常！")
        }
        logI("=================================================")
    }

    /**
     * 诊断结果数据类
     */
    data class DiagnosisResult(
        var hasVibratePermission: Boolean = false,
        var hasWakeLockPermission: Boolean = false,
        var hasPostNotificationPermission: Boolean = false,
        var canScheduleExactAlarms: Boolean = false,
        var areNotificationsEnabled: Boolean = false,
        var isChannelEnabled: Boolean = false,
        var isBatteryOptimizationIgnored: Boolean = false,
        var isDoNotDisturbActive: Boolean = false
    )
}