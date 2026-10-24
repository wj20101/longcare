package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.time.ServiceTimeProvider
import com.ytone.longcare.model.ServiceCountdownDeadline
import com.ytone.longcare.model.ServiceTimeSnapshot
import com.ytone.longcare.model.OrderLocalStateEntity
import com.ytone.longcare.model.LocalOrderStatus
import com.ytone.longcare.common.diagnostics.DiagnosticCategory
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class ServiceCountdownTimerDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val orderDetailRepository: OrderDetailRepository,
    private val viewModelScope: CoroutineScope,
    private val timeProvider: ServiceTimeProvider,
) {
    private var deadlineMillis: ServiceCountdownDeadline? = null
    private var displayUpdatesEnabled = false

    /** 读取持久起点并准备同一个截止时间；后台完成准备不会启动显示刷新。 */
    suspend fun synchronizeCountdown(orderKey: OrderKey, totalMinutes: Int): ServiceCountdownDeadline? {
        val diagnosticUserId = DiagnosticEventTracker.currentUserId()
        val localState = orderDetailRepository.getLocalState(orderKey)
        currentCoroutineContext().ensureActive()
        if (stopIfPersistedEnded(localState)) return null
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return null
        val startedAt = localState?.localStartTimestamp?.let {
            ServiceTimeSnapshot(it, localState.localStartElapsedRealtime ?: 0L,
                localState.localStartBootCount.takeIf { localState.localStartElapsedRealtime != null })
        } ?: run {
            // 兼容旧版本未保存的起点；磁盘等待不能额外延长服务时间。
            val fallbackStartedAt = timeProvider.snapshot()
            orderDetailRepository.startLocalService(orderKey, fallbackStartedAt)
            currentCoroutineContext().ensureActive()
            // 另一条初始化可能先保存了正式开始时间，以持久记录为准。
            val persisted = orderDetailRepository.getLocalState(orderKey)
            currentCoroutineContext().ensureActive()
            if (stopIfPersistedEnded(persisted)) return null
            persisted?.let {
                persisted.localStartTimestamp?.let {
                    ServiceTimeSnapshot(it, persisted.localStartElapsedRealtime ?: 0L,
                        persisted.localStartBootCount.takeIf { persisted.localStartElapsedRealtime != null })
                }
            } ?: fallbackStartedAt
        }
        currentCoroutineContext().ensureActive()
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return null
        val duration = TimeUnit.MINUTES.toMillis(totalMinutes.toLong())
        val deadline = ServiceCountdownDeadline(
            wallTimeMillis = startedAt.wallTimeMillis + duration,
            elapsedRealtimeMillis = startedAt.bootCount?.let { startedAt.elapsedRealtimeMillis + duration },
            bootCount = startedAt.bootCount,
        )
        if (deadlineMillis != deadline) {
            DiagnosticEventTracker.trackEvent(
                category = DiagnosticCategory.COUNTDOWN,
                event = "countdown_deadline_prepared",
                description = "倒计时截止时间来源",
                reportToServer = true,
                userId = diagnosticUserId,
                extras = mapOf(
                    "orderId" to orderKey.orderId,
                    "plannedAt" to deadline.triggerAtMillis(timeProvider.snapshot()),
                    "localStartedAt" to startedAt.wallTimeMillis,
                    "clockBasis" to if (deadline.usesMonotonicTime(timeProvider.snapshot())) "elapsed" else "wall_fallback",
                    "totalMinutes" to totalMinutes,
                    "startSource" to if (localState?.localStartTimestamp != null) "persisted_local" else "created_on_countdown",
                ),
            )
        }
        deadlineMillis = deadline
        if (displayUpdatesEnabled) startDisplayUpdates() else updateDisplay(deadline)
        return deadline
    }

    fun startDisplayUpdates() {
        displayUpdatesEnabled = true
        val deadline = deadlineMillis ?: return
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return
        stateHolder.countdownJob?.cancel()
        updateDisplay(deadline)
        stateHolder.countdownJob = viewModelScope.launch {
            while (isActive && stateHolder.countdownState.value != ServiceCountdownState.ENDED) {
                delay(1_000L)
                updateDisplay(deadline)
            }
        }
    }

    fun stopDisplayUpdates() {
        displayUpdatesEnabled = false
        stateHolder.countdownJob?.cancel()
        stateHolder.countdownJob = null
    }

    private fun stopIfPersistedEnded(localState: OrderLocalStateEntity?): Boolean {
        if (localState?.localStatus != LocalOrderStatus.COMPLETED.value) return false
        stateHolder.countdownState.value = ServiceCountdownState.ENDED
        stopDisplayUpdates()
        deadlineMillis = null
        return true
    }

    private fun updateDisplay(deadline: ServiceCountdownDeadline) {
        val remaining = deadline.remainingMillis(timeProvider.snapshot())
        // 计时到点仅进入超时展示，订单仍在服务，直到服务端确认签退或取消。
        stateHolder.countdownState.value = if (remaining > 0L) ServiceCountdownState.RUNNING else ServiceCountdownState.OVERTIME
        // 未到截止时间始终至少显示一秒；真实剩余毫秒和系统闹钟截止时间不变。
        val displaySeconds = if (remaining > 0L) (remaining - 1L) / 1_000L + 1L
            else TimeUnit.MILLISECONDS.toSeconds(-remaining)
        val hours = TimeUnit.SECONDS.toHours(displaySeconds)
        val minutes = TimeUnit.SECONDS.toMinutes(displaySeconds) % 60
        val seconds = displaySeconds % 60
        stateHolder.formattedTime.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
