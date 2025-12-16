package com.ytone.longcare.features.countdown.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.work.*
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.countdown.service.AlarmRingtoneService
import java.util.concurrent.TimeUnit
import androidx.core.content.edit

/**
 * 倒计时备份 Worker
 * 当 AlarmManager 因极端情况失败时作为保底机制
 * 
 * 注意：WorkManager 时间精度较低（可能延迟数分钟），仅作为保底
 */
class CountdownBackupWorker(
    context: Context,
    workerParams: WorkerParameters
) : Worker(context, workerParams) {
    
    companion object {
        private const val TAG = "CountdownBackupWorker"
        private const val WORK_NAME_PREFIX = "countdown_backup_"
        private const val PREFS_NAME = "countdown_backup_prefs"
        private const val KEY_ALARM_TRIGGERED_PREFIX = "alarm_triggered_"
        
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_SERVICE_NAME = "service_name"
        
        /**
         * 调度备份 Worker
         * @param context Context
         * @param orderId 订单ID
         * @param serviceName 服务名称
         * @param delayMinutes 延迟分钟数（应略大于实际倒计时时间）
         */
        fun schedule(
            context: Context,
            orderId: Long,
            serviceName: String,
            delayMinutes: Long
        ) {
            // 清除已触发标记
            clearAlarmTriggeredFlag(context, orderId)
            
            val data = workDataOf(
                KEY_ORDER_ID to orderId,
                KEY_SERVICE_NAME to serviceName
            )
            
            val workRequest = OneTimeWorkRequestBuilder<CountdownBackupWorker>()
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(data)
                .addTag("order_$orderId")
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false) // 即使低电量也要执行
                        .build()
                )
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "$WORK_NAME_PREFIX$orderId",
                    ExistingWorkPolicy.REPLACE,
                    workRequest
                )
            
            logI("$TAG: 备份 Worker 已调度 (orderId=$orderId, delay=${delayMinutes}min)")
        }
        
        /**
         * 取消指定订单的备份 Worker
         */
        fun cancel(context: Context, orderId: Long) {
            WorkManager.getInstance(context)
                .cancelUniqueWork("$WORK_NAME_PREFIX$orderId")
            clearAlarmTriggeredFlag(context, orderId)
            logI("$TAG: 备份 Worker 已取消 (orderId=$orderId)")
        }
        
        /**
         * 标记闹钟已触发（由 AlarmReceiver 调用）
         * 这样备份 Worker 运行时就知道不需要再次触发
         */
        fun markAlarmTriggered(context: Context, orderId: Long) {
            getPrefs(context).edit {
                putBoolean("$KEY_ALARM_TRIGGERED_PREFIX$orderId", true)
            }
            logI("$TAG: 已标记闹钟触发 (orderId=$orderId)")
        }
        
        /**
         * 检查闹钟是否已触发
         */
        private fun isAlarmTriggered(context: Context, orderId: Long): Boolean {
            return getPrefs(context).getBoolean("$KEY_ALARM_TRIGGERED_PREFIX$orderId", false)
        }
        
        /**
         * 清除闘钟触发标记
         */
        private fun clearAlarmTriggeredFlag(context: Context, orderId: Long) {
            getPrefs(context).edit {
                remove("$KEY_ALARM_TRIGGERED_PREFIX$orderId")
            }
        }

        private fun getPrefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }
    
    override fun doWork(): Result {
        val orderId = inputData.getLong(KEY_ORDER_ID, -1L)
        val serviceName = inputData.getString(KEY_SERVICE_NAME) ?: "未知服务"
        
        logI("$TAG: 备份 Worker 开始执行 (orderId=$orderId)")
        
        if (orderId == -1L) {
            logE("$TAG: 无效的 orderId")
            return Result.failure()
        }
        
        // 检查闹钟是否已经触发过
        if (isAlarmTriggered(applicationContext, orderId)) {
            logI("$TAG: 闹钟已正常触发，备份 Worker 跳过 (orderId=$orderId)")
            return Result.success()
        }
        
        // 闹钟未触发，执行备份逻辑
        logI("$TAG: ⚠️ 检测到闹钟未触发，启动备份响铃 (orderId=$orderId)")
        
        try {
            // 启动响铃服务
            AlarmRingtoneService.startRingtone(
                applicationContext, 
                orderId.toString(), 
                serviceName
            )
            logI("$TAG: ✅ 备份响铃已启动")
            
            // 标记已处理，避免重复
            markAlarmTriggered(applicationContext, orderId)
            
        } catch (e: Exception) {
            logE("$TAG: ❌ 备份响铃启动失败: ${e.message}")
            return Result.retry()
        }
        
        return Result.success()
    }
}
