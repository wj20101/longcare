package com.ytone.longcare.features.service

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class ServiceTimeFallbackScheduler {
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fallbackJobs = ConcurrentHashMap<Long, Job>()

    fun scheduleFallbackNotification(
        orderId: Long,
        delayMillis: Long,
        onTrigger: () -> Unit
    ) {
        try {
            cancelFallbackNotification(orderId)
            val job = fallbackScope.launch {
                try {
                    delay(delayMillis.coerceAtLeast(0L))
                    onTrigger()
                } catch (e: CancellationException) {
                    logI("Coroutine兜底任务已取消: orderId=$orderId")
                } catch (e: Exception) {
                    logE("Coroutine兜底通知执行失败: ${e.message}")
                } finally {
                    fallbackJobs.remove(orderId)
                }
            }
            fallbackJobs[orderId] = job
            logI("Coroutine兜底通知已设置: orderId=$orderId, delay=$delayMillis")
        } catch (e: Exception) {
            logE("设置Coroutine兜底通知失败: ${e.message}")
            throw e
        }
    }

    fun cancelFallbackNotification(orderId: Long) {
        val job = fallbackJobs.remove(orderId) ?: return
        job.cancel()
        logI("Coroutine兜底通知已取消: orderId=$orderId")
    }
}
