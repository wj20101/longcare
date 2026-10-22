package com.ytone.longcare.features.servicecountdown.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.domain.order.ServiceOrderLifecycle
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.result.ApiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ServiceCountdownViewModel @Inject constructor(
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val serviceOrderLifecycle: ServiceOrderLifecycle,
    private val systemGateway: ServiceCountdownSystemGateway,
) : ViewModel() {

    private data class CountdownServiceInfo(val serviceName: String, val totalMinutes: Int)
    private var synchronizationRequest: Pair<OrderKey, List<Int>>? = null
    private var synchronization: Deferred<Boolean>? = null

    private val stateHolder = ServiceCountdownStateHolder()
    private val timerDelegate = ServiceCountdownTimerDelegate(
        stateHolder = stateHolder,
        orderDetailRepository = unifiedOrderRepository,
        viewModelScope = viewModelScope
    )
    private val imageDelegate = ServiceCountdownImageDelegate(
        stateHolder = stateHolder,
        imageRepository = imageRepository,
        viewModelScope = viewModelScope
    )
    private val orderStatePollingDelegate = ServiceCountdownOrderStatePollingDelegate(
        stateHolder = stateHolder,
        serviceOrderLifecycle = serviceOrderLifecycle,
        viewModelScope = viewModelScope
    )

    val countdownState: StateFlow<ServiceCountdownState> = stateHolder.countdownState.asStateFlow()
    val formattedTime: StateFlow<String> = stateHolder.formattedTime.asStateFlow()
    val uploadedImages: StateFlow<Map<ImageTaskType, List<ImageTask>>> = stateHolder.uploadedImages.asStateFlow()
    val orderStateError: StateFlow<ServiceOrderStateModel?> = stateHolder.orderStateError.asStateFlow()

    /** 排程由 VM 持有；页面暂停不取消已确认服务的首次排程。 */
    fun synchronizeCountdownSession(
        orderKey: OrderKey,
        selectedProjectIds: List<Int>
    ): Deferred<Boolean> {
        val request = orderKey to selectedProjectIds.toList()
        val pending = synchronization
        if (synchronizationRequest == request && pending?.isActive == true) return pending
        pending?.cancel()
        synchronizationRequest = request
        val work = viewModelScope.async(start = CoroutineStart.LAZY) {
            synchronizeCountdown(orderKey, request.second)
        }
        synchronization = work
        work.start()
        return work
    }

    private suspend fun synchronizeCountdown(orderKey: OrderKey, selectedProjectIds: List<Int>): Boolean {
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return false
        val orderInfo = (unifiedOrderRepository.getOrderInfo(orderKey) as? ApiResult.Success)?.data ?: return false
        currentCoroutineContext().ensureActive()
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return false
        val projectList = orderInfo.projectList.orEmpty()
        val serviceInfo = calculateServiceInfo(projectList, selectedProjectIds)
        if (serviceInfo.totalMinutes <= 0) return false

        val deadline = timerDelegate.synchronizeCountdown(orderKey, serviceInfo.totalMinutes) ?: return false

        currentCoroutineContext().ensureActive()
        if (stateHolder.countdownState.value == ServiceCountdownState.ENDED) return false
        if (stateHolder.countdownState.value == ServiceCountdownState.RUNNING && deadline > System.currentTimeMillis()) {
            // 设置返回也走这里，以当前授权重新替换旧的降级闹钟；服务起点仍使用持久时间。
            systemGateway.scheduleCountdownAlarm(orderKey, serviceInfo.serviceName, deadline)
        }
        return true
    }

    fun startDisplayUpdates() = timerDelegate.startDisplayUpdates()
    fun stopDisplayUpdates() = timerDelegate.stopDisplayUpdates()

    /** 仅用于服务端已确认结束或取消；进入签退选择页不能改变服务状态。 */
    fun onOrderEnded(orderKey: OrderKey) {
        stateHolder.countdownState.value = ServiceCountdownState.ENDED
        synchronization?.cancel()
        stopDisplayUpdates()
        stopOrderStatePolling()
        systemGateway.cancelCountdownAlarmForOrder(orderKey)
        // 清栈会立即释放 VM；已确认的本地终态写入必须在导航前进入并完成。
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) { unifiedOrderRepository.endLocalService(orderKey) }
        }
    }
    fun canScheduleExactAlarms(): Boolean = systemGateway.canScheduleExactAlarms()
    fun canUseFullScreenIntent(): Boolean = systemGateway.canUseFullScreenIntent()

    fun startOrderStatePolling(orderKey: OrderKey) = orderStatePollingDelegate.startOrderStatePolling(orderKey)
    fun stopOrderStatePolling() = orderStatePollingDelegate.stopOrderStatePolling()
    fun clearOrderStateError() = orderStatePollingDelegate.clearOrderStateError()

    fun handlePhotoUploadResult(uploadResult: Map<ImageTaskType, List<ImageTask>>) = imageDelegate.handlePhotoUploadResult(uploadResult)
    fun validatePhotosUploaded(): Boolean = imageDelegate.validatePhotosUploaded()
    suspend fun getUploadedImagesSuspend(orderKey: OrderKey): Map<ImageTaskType, List<ImageTask>> = imageDelegate.getUploadedImagesSuspend(orderKey)
    fun loadUploadedImagesFromRepository(orderKey: OrderKey) = imageDelegate.loadUploadedImagesFromRepository(orderKey)

    private fun calculateServiceInfo(
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ): CountdownServiceInfo {
        val selectedProjects = projectList.filter { it.projectId in selectedProjectIds }
        return CountdownServiceInfo(
            serviceName = selectedProjects.joinToString(", ") { it.projectName.orEmpty() },
            totalMinutes = selectedProjects.sumOf { it.serviceTime }
        )
    }
}
