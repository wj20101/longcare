package com.ytone.longcare.features.servicecountdown.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.utils.SelectedProjectsManager
import com.ytone.longcare.common.utils.ServiceTimeManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.UploadedImagesManager
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownState
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.photoupload.model.ImageTask
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ServiceCountdownViewModel @Inject constructor(
    private val toastHelper: ToastHelper,
    private val serviceTimeManager: ServiceTimeManager,
    private val selectedProjectsManager: SelectedProjectsManager,
    private val uploadedImagesManager: UploadedImagesManager
) : ViewModel() {
    
    // 倒计时状态
    private val _countdownState = MutableStateFlow(ServiceCountdownState.RUNNING)
    val countdownState: StateFlow<ServiceCountdownState> = _countdownState.asStateFlow()
    
    // 倒计时时间（毫秒）
    private val _remainingTimeMillis = MutableStateFlow(0L)
    val remainingTimeMillis: StateFlow<Long> = _remainingTimeMillis.asStateFlow()
    
    // 格式化的倒计时时间
    private val _formattedTime = MutableStateFlow("12:00:00")
    val formattedTime: StateFlow<String> = _formattedTime.asStateFlow()
    
    // 超时时间（毫秒）
    private val _overtimeMillis = MutableStateFlow(0L)
    val overtimeMillis: StateFlow<Long> = _overtimeMillis.asStateFlow()
    
    // 倒计时Job
    private var countdownJob: Job? = null
    
    // 已上传的图片数据
    private val _uploadedImages = MutableStateFlow<Map<ImageTaskType, List<ImageTask>>>(emptyMap())
    val uploadedImages: StateFlow<Map<ImageTaskType, List<ImageTask>>> = _uploadedImages.asStateFlow()
    
    // 当前订单和项目信息，用于超时计时中重新计算时间
    private var currentOrderId: Long = 0
    private var currentProjectList: List<ServiceProjectM> = emptyList()
    private var currentSelectedProjectIds: List<Int> = emptyList()
    
    /**
     * 根据项目列表设置倒计时时间
     * @param orderRequest 订单信息请求模型
     * @param projectList 所有项目列表
     * @param selectedProjectIds 选中的项目ID列表
     */
    fun setCountdownTimeFromProjects(
        orderRequest: OrderInfoRequestModel,
        projectList: List<ServiceProjectM>, 
        selectedProjectIds: List<Int>
    ) {
        // 保存当前订单ID和项目信息，用于后续重新计算
        currentOrderId = orderRequest.orderId
        currentProjectList = projectList
        currentSelectedProjectIds = selectedProjectIds
        
        // 计算并更新倒计时状态
        val (state, remainingTime, overtimeTime) = calculateCountdownState(
            orderRequest.orderId,
            projectList,
            selectedProjectIds
        )
        
        // 更新状态
        _countdownState.value = state
        _remainingTimeMillis.value = remainingTime
        _overtimeMillis.value = overtimeTime
        updateFormattedTime()
        
        // 根据状态启动相应的倒计时
        when (state) {
            ServiceCountdownState.RUNNING -> startCountdown()
            ServiceCountdownState.OVERTIME -> startOvertimeCountdown()
            else -> {
                // COMPLETED 或 ENDED 状态不需要启动倒计时
                countdownJob?.cancel()
            }
        }
    }
    
    /**
     * 计算倒计时状态（统一的时间计算逻辑）
     * 
     * 这是唯一的时间计算入口，确保：
     * 1. UI显示的倒计时时间
     * 2. 前台服务通知的时间
     * 3. 系统闹钟的触发时间
     * 都使用相同的计算逻辑，避免时间不一致的问题
     * 
     * @param orderId 订单ID
     * @param projectList 所有项目列表
     * @param selectedProjectIds 选中的项目ID列表
     * @return Triple(状态, 剩余时间毫秒, 超时时间毫秒)
     */
    private fun calculateCountdownState(
        orderId: Long,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ): Triple<ServiceCountdownState, Long, Long> {
        // 计算总服务时长（分钟）
        val totalMinutes = projectList
            .filter { it.projectId in selectedProjectIds }
            .sumOf { it.serviceTime }
        
        if (totalMinutes <= 0) {
            return Triple(ServiceCountdownState.ENDED, 0L, 0L)
        }
        
        // 获取或创建服务开始时间
        val serviceStartTime = serviceTimeManager.getOrCreateServiceStartTime(orderId)
        
        // 计算总服务时长（毫秒）
        val totalServiceTimeMillis = totalMinutes * 60 * 1000L
        
        // 计算已经过去的时间
        val elapsedTime = System.currentTimeMillis() - serviceStartTime
        
        // 计算剩余时间
        val remainingTime = totalServiceTimeMillis - elapsedTime
        
        return if (remainingTime > 0) {
            // 还有剩余时间，正常倒计时
            Triple(ServiceCountdownState.RUNNING, remainingTime, 0L)
        } else {
            // 已经超时
            val overtimeMillis = -remainingTime
            Triple(ServiceCountdownState.OVERTIME, 0L, overtimeMillis)
        }
    }
    
    /**
     * 获取当前倒计时状态（用于前台服务等外部调用）
     * @return Triple(状态, 剩余时间毫秒, 超时时间毫秒)
     */
    fun getCurrentCountdownState(): Triple<ServiceCountdownState, Long, Long> {
        if (currentOrderId == 0L || currentProjectList.isEmpty()) {
            return Triple(_countdownState.value, _remainingTimeMillis.value, _overtimeMillis.value)
        }
        
        // 重新计算最新的状态
        return calculateCountdownState(
            currentOrderId,
            currentProjectList,
            currentSelectedProjectIds
        )
    }
    
    // 启动超时计时
    private fun startOvertimeCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_countdownState.value == ServiceCountdownState.OVERTIME) {
                delay(1000)
                
                // 重新计算当前的超时时间，确保锁屏解锁后时间正确
                if (currentOrderId != 0L && currentProjectList.isNotEmpty()) {
                    val (_, _, overtimeMillis) = calculateCountdownState(
                        currentOrderId,
                        currentProjectList,
                        currentSelectedProjectIds
                    )
                    _overtimeMillis.value = overtimeMillis
                }
                
                updateFormattedTime()
            }
        }
    }
    

    
    // 启动倒计时
    fun startCountdown() {
        // 取消之前的倒计时
        countdownJob?.cancel()
        
        // 设置状态为运行中
        _countdownState.value = ServiceCountdownState.RUNNING
        
        // 启动新的倒计时
        countdownJob = viewModelScope.launch {
            // 倒计时阶段
            while (_remainingTimeMillis.value > 0) {
                // 更新格式化时间
                updateFormattedTime()
                
                // 延迟1秒
                delay(1000)
                
                // 减少剩余时间
                _remainingTimeMillis.value -= 1000
            }
            
            // 倒计时结束，立即进入超时计时阶段
            _remainingTimeMillis.value = 0
            _countdownState.value = ServiceCountdownState.COMPLETED
            updateFormattedTime()
            
            // 立即进入超时状态（移除5秒延迟）
            _countdownState.value = ServiceCountdownState.OVERTIME
            _overtimeMillis.value = 0
            
            // 超时计时阶段
            while (_countdownState.value == ServiceCountdownState.OVERTIME) {
                // 延迟1秒
                delay(1000)
                
                // 增加超时时间
                _overtimeMillis.value += 1000
                
                // 更新格式化时间显示超时时长
                updateFormattedTime()
            }
        }
    }
    
    /**
     * 启动前台服务显示倒计时通知
     * @param context 上下文
     * @param orderId 订单ID
     * @param serviceName 服务名称
     * @param totalSeconds 总倒计时秒数
     */
    fun startForegroundService(
        context: Context,
        orderId: Long,
        serviceName: String,
        totalSeconds: Long
    ) {
        CountdownForegroundService.startCountdown(context, orderId, serviceName, totalSeconds)
    }
    
    /**
     * 停止前台服务
     * @param context 上下文
     */
    fun stopForegroundService(context: Context) {
        CountdownForegroundService.stopCountdown(context)
    }
    
    /**
     * 更新前台服务的倒计时时间（已废弃，通知改为静态显示）
     * @param context 上下文
     * @param remainingSeconds 剩余秒数
     * @param serviceName 服务名称
     */
    @Deprecated("通知已改为静态显示，不再需要更新时间")
    fun updateForegroundServiceTime(
        context: Context,
        remainingSeconds: Long,
        serviceName: String
    ) {
        // 不再执行任何操作
        // CountdownForegroundService.updateTime(context, remainingSeconds, serviceName)
    }
    
    // 更新格式化时间
    private fun updateFormattedTime() {
        val timeToFormat = if (_countdownState.value == ServiceCountdownState.OVERTIME) {
            _overtimeMillis.value
        } else {
            _remainingTimeMillis.value
        }
        
        val hours = TimeUnit.MILLISECONDS.toHours(timeToFormat)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(timeToFormat) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(timeToFormat) % 60

        _formattedTime.value = String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    // 暂停倒计时
    fun pauseCountdown() {
        countdownJob?.cancel()
    }
    
    // 重置倒计时
    fun resetCountdown(totalMinutes: Int = 0) {
        countdownJob?.cancel()
        _remainingTimeMillis.value = totalMinutes * 60 * 1000L
        updateFormattedTime()
        _countdownState.value = ServiceCountdownState.RUNNING
    }
    
    /**
     * 结束服务
     * @param orderInfoRequest 订单信息请求模型，用于清除服务时间记录和选中项目记录
     * @param context 上下文，用于停止前台服务
     */
    fun endService(orderInfoRequest: OrderInfoRequestModel, context: Context? = null) {
        countdownJob?.cancel()
        _countdownState.value = ServiceCountdownState.ENDED
        
        // 停止前台服务
        context?.let { stopForegroundService(it) }
        
        // 清除服务时间记录和选中项目记录
        serviceTimeManager.clearServiceTime(orderInfoRequest.orderId)
        selectedProjectsManager.clearSelectedProjects(orderInfoRequest.orderId)
        // 清除本地存储的图片数据
        clearUploadedImagesFromLocal(orderInfoRequest)
    }
    
    // 设置倒计时时间（用于测试或手动设置）
    fun setCountdownTime(hours: Long, minutes: Long, seconds: Long) {
        val totalMillis = TimeUnit.HOURS.toMillis(hours) +
                          TimeUnit.MINUTES.toMillis(minutes) +
                          TimeUnit.SECONDS.toMillis(seconds)
        
        _remainingTimeMillis.value = totalMillis
        updateFormattedTime()
    }
    
    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
    
    /**
     * 处理图片上传结果
     * @param orderRequest 订单信息请求模型
     * @param uploadResult 按ImageTaskType分组的ImageTask列表
     */
    fun handlePhotoUploadResult(orderRequest: OrderInfoRequestModel, uploadResult: Map<ImageTaskType, List<ImageTask>>) {
        val beforeCareTasks = uploadResult[ImageTaskType.BEFORE_CARE] ?: emptyList()
        val afterCareTasks = uploadResult[ImageTaskType.AFTER_CARE] ?: emptyList()
        
        // 保存上传的图片数据到状态中
        _uploadedImages.value = uploadResult
        
        // 保存到本地存储，与订单关联
        uploadedImagesManager.saveUploadedImages(orderRequest, uploadResult)
        
        println("收到护理前图片: $beforeCareTasks")
        println("收到护理后图片: $afterCareTasks")
        println("已保存图片数据到ViewModel状态和本地存储中")
    }
    
    /**
     * 获取当前已上传的图片数据
     * @return 按ImageTaskType分组的ImageTask列表
     */
    fun getCurrentUploadedImages(): Map<ImageTaskType, List<ImageTask>> {
        return _uploadedImages.value
    }
    
    /**
     * 验证照片是否已上传
     * @return true表示护理前和护理后照片都已上传，false表示有照片未上传
     */
    fun validatePhotosUploaded(): Boolean {
        val uploadedImages = _uploadedImages.value
        val beforeCareTasks = uploadedImages[ImageTaskType.BEFORE_CARE] ?: emptyList()
        val afterCareTasks = uploadedImages[ImageTaskType.AFTER_CARE] ?: emptyList()
        
        return beforeCareTasks.isNotEmpty() && afterCareTasks.isNotEmpty()
    }
    
    /**
     * 加载本地存储的图片数据（用于页面恢复）
     * @param orderRequest 订单信息请求模型
     */
    fun loadUploadedImagesFromLocal(orderRequest: OrderInfoRequestModel) {
        val localImages = uploadedImagesManager.getUploadedImages(orderRequest)
        if (localImages.isNotEmpty()) {
            _uploadedImages.value = localImages
            println("从本地存储加载图片数据: $localImages")
        }
    }
    
    /**
     * 检查是否有本地存储的图片数据
     * @param orderRequest 订单信息请求模型
     * @return 是否存在本地存储的图片数据
     */
    fun hasLocalUploadedImages(orderRequest: OrderInfoRequestModel): Boolean {
        return uploadedImagesManager.hasUploadedImages(orderRequest)
    }
    
    /**
     * 清除本地存储的图片数据（订单完成后调用）
     * @param orderRequest 订单信息请求模型
     */
    fun clearUploadedImagesFromLocal(orderRequest: OrderInfoRequestModel) {
        uploadedImagesManager.deleteUploadedImages(orderRequest)
        println("已清除订单 ${orderRequest.orderId} 的本地图片数据")
    }

    /**
     * 显示Toast提示
     * @param message 提示信息
     */
    fun showToast(message: String) {
        toastHelper.showShort(message)
    }
}