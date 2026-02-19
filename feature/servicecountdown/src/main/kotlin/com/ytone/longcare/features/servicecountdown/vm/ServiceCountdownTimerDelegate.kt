package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceProjectM
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class ServiceCountdownTimerDelegate(
    private val stateHolder: ServiceCountdownStateHolder,
    private val orderDetailRepository: OrderDetailRepository,
    private val viewModelScope: CoroutineScope,
) {
    suspend fun applyCountdownState(
        orderKey: OrderKey,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>,
        startTicker: Boolean
    ): Triple<ServiceCountdownState, Long, Long> {
        stateHolder.currentOrderKey = orderKey
        stateHolder.currentProjectList = projectList
        stateHolder.currentSelectedProjectIds = selectedProjectIds

        val (state, remainingTime, overtimeTime) = calculateCountdownState(
            orderKey = orderKey,
            projectList = projectList,
            selectedProjectIds = selectedProjectIds
        )

        stateHolder.countdownState.value = state
        stateHolder.remainingTimeMillis.value = remainingTime
        stateHolder.overtimeMillis.value = overtimeTime
        updateFormattedTime()

        if (startTicker) {
            when (state) {
                ServiceCountdownState.RUNNING -> startCountdown()
                ServiceCountdownState.OVERTIME -> startOvertimeCountdown()
                else -> stateHolder.countdownJob?.cancel()
            }
        }
        return Triple(state, remainingTime, overtimeTime)
    }

    suspend fun calculateCountdownState(
        orderKey: OrderKey,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ): Triple<ServiceCountdownState, Long, Long> {
        val totalMinutes = projectList
            .filter { it.projectId in selectedProjectIds }
            .sumOf { it.serviceTime }
        if (totalMinutes <= 0) {
            return Triple(ServiceCountdownState.ENDED, 0L, 0L)
        }

        val localState = orderDetailRepository.getLocalState(orderKey)
        val serviceStartTime = localState?.localStartTimestamp ?: run {
            orderDetailRepository.startLocalService(orderKey)
            System.currentTimeMillis()
        }

        val totalServiceTimeMillis = totalMinutes * 60 * 1000L
        val elapsedTime = System.currentTimeMillis() - serviceStartTime
        val remainingTime = totalServiceTimeMillis - elapsedTime
        return if (remainingTime > 0) {
            Triple(ServiceCountdownState.RUNNING, remainingTime, 0L)
        } else {
            Triple(ServiceCountdownState.OVERTIME, 0L, -remainingTime)
        }
    }

    fun getCurrentCountdownState(): Triple<ServiceCountdownState, Long, Long> {
        return Triple(
            stateHolder.countdownState.value,
            stateHolder.remainingTimeMillis.value,
            stateHolder.overtimeMillis.value
        )
    }

    fun refreshCountdownDisplay(
        orderKey: OrderKey,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ) {
        stateHolder.currentOrderKey = orderKey
        stateHolder.currentProjectList = projectList
        stateHolder.currentSelectedProjectIds = selectedProjectIds

        viewModelScope.launch {
            val (state, remainingTime, overtimeTime) = calculateCountdownState(
                orderKey = orderKey,
                projectList = projectList,
                selectedProjectIds = selectedProjectIds
            )
            stateHolder.remainingTimeMillis.value = remainingTime
            stateHolder.overtimeMillis.value = overtimeTime
            updateFormattedTime()

            if (stateHolder.countdownState.value != state) {
                stateHolder.countdownState.value = state
                if (state == ServiceCountdownState.OVERTIME && stateHolder.countdownJob?.isActive != true) {
                    startOvertimeCountdown()
                }
            }
        }
    }

    fun startCountdown() {
        stateHolder.countdownJob?.cancel()
        stateHolder.countdownState.value = ServiceCountdownState.RUNNING

        stateHolder.countdownJob = viewModelScope.launch {
            while (isActive && stateHolder.countdownState.value == ServiceCountdownState.RUNNING) {
                if (stateHolder.currentOrderKey != null && stateHolder.currentProjectList.isNotEmpty()) {
                    val (state, remainingTime, _) = calculateCountdownState(
                        orderKey = stateHolder.currentOrderKey!!,
                        projectList = stateHolder.currentProjectList,
                        selectedProjectIds = stateHolder.currentSelectedProjectIds
                    )
                    if (state != ServiceCountdownState.RUNNING) {
                        break
                    }
                    stateHolder.remainingTimeMillis.value = remainingTime
                }
                updateFormattedTime()
                delay(1000)
            }

            if (stateHolder.currentOrderKey != null && stateHolder.currentProjectList.isNotEmpty()) {
                val (state, _, overtimeMillis) = calculateCountdownState(
                    orderKey = stateHolder.currentOrderKey!!,
                    projectList = stateHolder.currentProjectList,
                    selectedProjectIds = stateHolder.currentSelectedProjectIds
                )
                if (state == ServiceCountdownState.OVERTIME) {
                    stateHolder.remainingTimeMillis.value = 0
                    stateHolder.countdownState.value = ServiceCountdownState.COMPLETED
                    updateFormattedTime()

                    delay(100)
                    stateHolder.countdownState.value = ServiceCountdownState.OVERTIME
                    stateHolder.overtimeMillis.value = overtimeMillis
                    updateFormattedTime()
                    startOvertimeCountdown()
                }
            }
        }
    }

    fun pauseCountdown() {
        stateHolder.countdownJob?.cancel()
    }

    fun resetCountdown(totalMinutes: Int = 0) {
        stateHolder.countdownJob?.cancel()
        stateHolder.remainingTimeMillis.value = totalMinutes * 60 * 1000L
        updateFormattedTime()
        stateHolder.countdownState.value = ServiceCountdownState.RUNNING
    }

    fun setCountdownTime(hours: Long, minutes: Long, seconds: Long) {
        val totalMillis = TimeUnit.HOURS.toMillis(hours) +
            TimeUnit.MINUTES.toMillis(minutes) +
            TimeUnit.SECONDS.toMillis(seconds)
        stateHolder.remainingTimeMillis.value = totalMillis
        updateFormattedTime()
    }

    private fun startOvertimeCountdown() {
        stateHolder.countdownJob?.cancel()
        stateHolder.countdownJob = viewModelScope.launch {
            while (isActive && stateHolder.countdownState.value == ServiceCountdownState.OVERTIME) {
                delay(1000)
                if (stateHolder.currentOrderKey != null && stateHolder.currentProjectList.isNotEmpty()) {
                    val (_, _, overtimeMillis) = calculateCountdownState(
                        orderKey = stateHolder.currentOrderKey!!,
                        projectList = stateHolder.currentProjectList,
                        selectedProjectIds = stateHolder.currentSelectedProjectIds
                    )
                    stateHolder.overtimeMillis.value = overtimeMillis
                }
                updateFormattedTime()
            }
        }
    }

    private fun updateFormattedTime() {
        val timeToFormat = if (stateHolder.countdownState.value == ServiceCountdownState.OVERTIME) {
            stateHolder.overtimeMillis.value
        } else {
            stateHolder.remainingTimeMillis.value
        }
        val hours = TimeUnit.MILLISECONDS.toHours(timeToFormat)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeToFormat) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeToFormat) % 60
        stateHolder.formattedTime.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
}
