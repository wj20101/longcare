package com.ytone.longcare.features.servicecountdown.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.ServiceProjectM
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ServiceCountdownViewModel @Inject constructor(
    private val toastHelper: ToastHelper,
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val orderRepository: OrderRepository,
    private val systemGateway: ServiceCountdownSystemGateway,
    private val runtimeConfigProvider: RuntimeConfigProvider,
) : ViewModel() {

    private data class CountdownInitializationState(
        val isInitialized: Boolean = false,
        val lastProjectIdList: List<Int> = emptyList(),
        val permissionsChecked: Boolean = false
    )

    private data class CountdownServiceInfo(val serviceName: String, val totalMinutes: Int)

    private val initializationState = MutableStateFlow(CountdownInitializationState())
    private val stateHolder = ServiceCountdownStateHolder()
    private val _orderStateErrorEvents = MutableSharedFlow<ServiceOrderStateModel>(replay = 0, extraBufferCapacity = 1)

    private val timerDelegate = ServiceCountdownTimerDelegate(
        stateHolder = stateHolder,
        orderDetailRepository = unifiedOrderRepository,
        viewModelScope = viewModelScope
    )
    private val serviceDelegate = ServiceCountdownServiceDelegate(
        stateHolder = stateHolder,
        orderDetailRepository = unifiedOrderRepository,
        imageRepository = imageRepository,
        systemGateway = systemGateway,
        viewModelScope = viewModelScope
    )
    private val imageDelegate = ServiceCountdownImageDelegate(
        stateHolder = stateHolder,
        imageRepository = imageRepository,
        viewModelScope = viewModelScope
    )
    private val orderStatePollingDelegate = ServiceCountdownOrderStatePollingDelegate(
        stateHolder = stateHolder,
        orderRepository = orderRepository,
        orderStateErrorEvents = _orderStateErrorEvents,
        viewModelScope = viewModelScope
    )

    val isMockDataEnabled: Boolean get() = runtimeConfigProvider.useMockData
    val countdownState: StateFlow<ServiceCountdownState> = stateHolder.countdownState.asStateFlow()
    val remainingTimeMillis: StateFlow<Long> = stateHolder.remainingTimeMillis.asStateFlow()
    val formattedTime: StateFlow<String> = stateHolder.formattedTime.asStateFlow()
    val overtimeMillis: StateFlow<Long> = stateHolder.overtimeMillis.asStateFlow()
    val uploadedImages: StateFlow<Map<ImageTaskType, List<ImageTask>>> = stateHolder.uploadedImages.asStateFlow()
    val orderStateError: StateFlow<ServiceOrderStateModel?> = stateHolder.orderStateError.asStateFlow()
    val orderStateErrorEvents: SharedFlow<ServiceOrderStateModel> = _orderStateErrorEvents.asSharedFlow()

    fun setCountdownTimeFromProjects(orderKey: OrderKey, projectList: List<ServiceProjectM>, selectedProjectIds: List<Int>) {
        viewModelScope.launch {
            timerDelegate.applyCountdownState(orderKey, projectList, selectedProjectIds, startTicker = true)
        }
    }

    fun shouldReinitialize(selectedProjectIds: List<Int>): Boolean {
        val init = initializationState.value
        return !init.isInitialized || init.lastProjectIdList != selectedProjectIds || stateHolder.countdownState.value == ServiceCountdownState.ENDED
    }

    fun shouldCheckPermissions(): Boolean = !initializationState.value.permissionsChecked

    fun markPermissionsChecked() {
        initializationState.value = initializationState.value.copy(permissionsChecked = true)
    }

    fun markInitialized(selectedProjectIds: List<Int>) {
        initializationState.value = initializationState.value.copy(isInitialized = true, lastProjectIdList = selectedProjectIds)
    }

    fun isInitialized(): Boolean = initializationState.value.isInitialized

    suspend fun initializeCountdownSession(
        context: Context,
        orderKey: OrderKey,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ): Boolean {
        val serviceInfo = calculateServiceInfo(projectList, selectedProjectIds)
        if (serviceInfo.totalMinutes <= 0) return false

        val (state, remainingMillis, _) = timerDelegate.applyCountdownState(
            orderKey = orderKey,
            projectList = projectList,
            selectedProjectIds = selectedProjectIds,
            startTicker = true
        )

        startForegroundService(context, orderKey, serviceInfo.serviceName, serviceInfo.totalMinutes * 60L)
        if (state == ServiceCountdownState.RUNNING && remainingMillis > 0) {
            scheduleCountdownAlarm(orderKey, serviceInfo.serviceName, System.currentTimeMillis() + remainingMillis)
        }
        return true
    }

    fun getCurrentCountdownState(): Triple<ServiceCountdownState, Long, Long> = timerDelegate.getCurrentCountdownState()
    fun refreshCountdownDisplay(orderKey: OrderKey, projectList: List<ServiceProjectM>, selectedProjectIds: List<Int>) =
        timerDelegate.refreshCountdownDisplay(orderKey, projectList, selectedProjectIds)
    fun startCountdown() = timerDelegate.startCountdown()
    fun pauseCountdown() = timerDelegate.pauseCountdown()
    fun resetCountdown(totalMinutes: Int = 0) = timerDelegate.resetCountdown(totalMinutes)
    fun setCountdownTime(hours: Long, minutes: Long, seconds: Long) = timerDelegate.setCountdownTime(hours, minutes, seconds)

    fun startForegroundService(context: Context, orderKey: OrderKey, serviceName: String, totalSeconds: Long) =
        serviceDelegate.startForegroundService(context, orderKey, serviceName, totalSeconds)
    fun stopForegroundService(context: Context) = serviceDelegate.stopForegroundService(context)
    fun endService(orderKey: OrderKey, context: Context? = null) = serviceDelegate.endService(orderKey, context)
    fun endServiceWithoutClearingImages(orderKey: OrderKey, context: Context? = null) =
        serviceDelegate.endServiceWithoutClearingImages(orderKey, context)
    fun canScheduleExactAlarms(): Boolean = serviceDelegate.canScheduleExactAlarms()
    fun canUseFullScreenIntent(): Boolean = serviceDelegate.canUseFullScreenIntent()
    fun scheduleCountdownAlarm(orderKey: OrderKey, serviceName: String, triggerTimeMillis: Long) =
        serviceDelegate.scheduleCountdownAlarm(orderKey, serviceName, triggerTimeMillis)
    fun cancelCountdownAlarm() = serviceDelegate.cancelCountdownAlarm()
    fun cancelCountdownAlarmForOrder(orderKey: OrderKey) = serviceDelegate.cancelCountdownAlarmForOrder(orderKey)

    fun startOrderStatePolling(orderKey: OrderKey) = orderStatePollingDelegate.startOrderStatePolling(orderKey)
    fun stopOrderStatePolling() = orderStatePollingDelegate.stopOrderStatePolling()
    fun clearOrderStateError() = orderStatePollingDelegate.clearOrderStateError()

    fun handlePhotoUploadResult(uploadResult: Map<ImageTaskType, List<ImageTask>>) = imageDelegate.handlePhotoUploadResult(uploadResult)
    fun getCurrentUploadedImages(): Map<ImageTaskType, List<ImageTask>> = imageDelegate.getCurrentUploadedImages()
    fun validatePhotosUploaded(): Boolean = imageDelegate.validatePhotosUploaded()
    suspend fun getUploadedImagesSuspend(orderKey: OrderKey): Map<ImageTaskType, List<ImageTask>> = imageDelegate.getUploadedImagesSuspend(orderKey)
    fun loadUploadedImagesFromRepository(orderKey: OrderKey) = imageDelegate.loadUploadedImagesFromRepository(orderKey)
    suspend fun hasLocalUploadedImages(orderKey: OrderKey): Boolean = imageDelegate.hasLocalUploadedImages(orderKey)
    fun clearUploadedImagesFromLocal(orderKey: OrderKey) = imageDelegate.clearUploadedImagesFromLocal(orderKey)

    fun showToast(message: String) {
        toastHelper.showShort(message)
    }

    override fun onCleared() {
        super.onCleared()
        serviceDelegate.onCleared()
    }

    private fun calculateServiceInfo(
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ): CountdownServiceInfo {
        val selectedProjects = projectList.filter { it.projectId in selectedProjectIds }
        return CountdownServiceInfo(
            serviceName = selectedProjects.joinToString(", ") { it.projectName },
            totalMinutes = selectedProjects.sumOf { it.serviceTime }
        )
    }
}
