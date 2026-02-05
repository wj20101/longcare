package com.ytone.longcare.features.servicecountdown.vm

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.request.OrderInfoRequestModel
import com.ytone.longcare.api.response.ServiceOrderStateModel
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.repository.ImageRepository
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.toOrderKey
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.features.photoupload.model.ImageTask
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import com.ytone.longcare.features.servicecountdown.service.CountdownForegroundService
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownState
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
    private val unifiedOrderRepository: UnifiedOrderRepository,
    private val imageRepository: ImageRepository,
    private val orderRepository: OrderRepository
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
    
    // 订单状态轮询Job
    private var orderStatePollingJob: Job? = null
    
    // 已上传的图片数据
    private val _uploadedImages = MutableStateFlow<Map<ImageTaskType, List<ImageTask>>>(emptyMap())
    val uploadedImages: StateFlow<Map<ImageTaskType, List<ImageTask>>> = _uploadedImages.asStateFlow()
    
    // 订单状态异常事件（用于通知UI显示弹窗）
    private val _orderStateError = MutableStateFlow<ServiceOrderStateModel?>(null)
    val orderStateError: StateFlow<ServiceOrderStateModel?> = _orderStateError.asStateFlow()
    
    // 当前订单和项目信息，用于超时计时中重新计算时间
    private var currentOrderKey: OrderKey? = null
    private var currentProjectList: List<ServiceProjectM> = emptyList()
    private var currentSelectedProjectIds: List<Int> = emptyList()
    
    companion object {
        /** 订单状态轮询间隔（毫秒） */
        private const val ORDER_STATE_POLLING_INTERVAL = 5000L
    }
    
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
        currentOrderKey = orderRequest.toOrderKey()
        currentProjectList = projectList
        currentSelectedProjectIds = selectedProjectIds
        
        // 在协程中计算并更新倒计时状态
        viewModelScope.launch {
            val (state, remainingTime, overtimeTime) = calculateCountdownState(
                orderRequest.toOrderKey(),
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
     * @param orderKey 订单标识符
     * @param projectList 所有项目列表
     * @param selectedProjectIds 选中的项目ID列表
     * @return Triple(状态, 剩余时间毫秒, 超时时间毫秒)
     */
    private suspend fun calculateCountdownState(
        orderKey: OrderKey,
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
        val localState = unifiedOrderRepository.getLocalState(orderKey)
        val serviceStartTime = localState?.localStartTimestamp
            ?: run {
                // 没有记录，开始服务
                unifiedOrderRepository.startLocalService(orderKey)
                System.currentTimeMillis()
            }
        
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
     * 返回缓存的状态值，不涉及IO操作
     * @return Triple(状态, 剩余时间毫秒, 超时时间毫秒)
     */
    fun getCurrentCountdownState(): Triple<ServiceCountdownState, Long, Long> {
        return Triple(_countdownState.value, _remainingTimeMillis.value, _overtimeMillis.value)
    }
    
    /**
     * 仅刷新倒计时显示，不重新启动倒计时
     * 用于生命周期恢复时更新UI显示，避免重复初始化
     * 
     * @param orderRequest 订单信息请求模型
     * @param projectList 所有项目列表
     * @param selectedProjectIds 选中的项目ID列表
     */
    fun refreshCountdownDisplay(
        orderRequest: OrderInfoRequestModel,
        projectList: List<ServiceProjectM>,
        selectedProjectIds: List<Int>
    ) {
        // 保存当前订单ID和项目信息
        currentOrderKey = orderRequest.toOrderKey()
        currentProjectList = projectList
        currentSelectedProjectIds = selectedProjectIds
        
        // 在协程中重新计算当前状态
        viewModelScope.launch {
            val (state, remainingTime, overtimeTime) = calculateCountdownState(
                orderRequest.toOrderKey(),
                projectList,
                selectedProjectIds
            )
            
            // 仅更新显示值，不改变倒计时Job的运行状态
            _remainingTimeMillis.value = remainingTime
            _overtimeMillis.value = overtimeTime
            updateFormattedTime()
            
            // 如果状态发生变化（比如从RUNNING变为OVERTIME），才更新状态
            if (_countdownState.value != state) {
                _countdownState.value = state
                
                // 如果进入超时状态且倒计时Job未运行，启动超时计时
                if (state == ServiceCountdownState.OVERTIME && countdownJob?.isActive != true) {
                    startOvertimeCountdown()
                }
            }
        }
    }
    
    // 启动超时计时
    private fun startOvertimeCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            while (_countdownState.value == ServiceCountdownState.OVERTIME) {
                delay(1000)
                
                // 重新计算当前的超时时间，确保锁屏解锁后时间正确
                // 使用重新计算而不是累加，避免时间不准确
                if (currentOrderKey != null && currentProjectList.isNotEmpty()) {
                    val (_, _, overtimeMillis) = calculateCountdownState(
                        currentOrderKey!!,
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
            // 倒计时阶段 - 使用重新计算而不是递减，确保时间准确
            while (_countdownState.value == ServiceCountdownState.RUNNING) {
                // 重新计算剩余时间，避免累加误差
                if (currentOrderKey != null && currentProjectList.isNotEmpty()) {
                    val (state, remainingTime, _) = calculateCountdownState(
                        currentOrderKey!!,
                        currentProjectList,
                        currentSelectedProjectIds
                    )
                    
                    if (state != ServiceCountdownState.RUNNING) {
                        // 状态变化，退出循环
                        break
                    }
                    
                    _remainingTimeMillis.value = remainingTime
                }
                
                // 更新格式化时间
                updateFormattedTime()
                
                // 延迟1秒
                delay(1000)
            }
            
            // 检查是否进入超时状态
            if (currentOrderKey != null && currentProjectList.isNotEmpty()) {
                val (state, _, overtimeMillis) = calculateCountdownState(
                    currentOrderKey!!,
                    currentProjectList,
                    currentSelectedProjectIds
                )
                
                if (state == ServiceCountdownState.OVERTIME) {
                    // 倒计时结束，进入超时状态
                    _remainingTimeMillis.value = 0
                    _countdownState.value = ServiceCountdownState.COMPLETED
                    updateFormattedTime()
                    
                    // 短暂延迟后进入超时状态
                    delay(100)
                    _countdownState.value = ServiceCountdownState.OVERTIME
                    _overtimeMillis.value = overtimeMillis
                    updateFormattedTime()
                    
                    // 启动超时计时
                    startOvertimeCountdown()
                }
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
        orderStatePollingJob?.cancel()
        _countdownState.value = ServiceCountdownState.ENDED
        
        // 停止前台服务
        context?.let { stopForegroundService(it) }
        
        // 使用unifiedOrderRepository结束服务并清除图片数据
        viewModelScope.launch {
            unifiedOrderRepository.endLocalService(orderInfoRequest.toOrderKey())
            imageRepository.deleteImagesByOrderId(orderInfoRequest.toOrderKey())
        }
    }
    
    /**
     * 结束服务但不清除图片数据
     * 用于订单状态异常时的清理，保留图片数据以便订单重新开始时使用
     * 
     * @param orderInfoRequest 订单信息请求模型
     * @param context 上下文，用于停止前台服务
     */
    fun endServiceWithoutClearingImages(orderInfoRequest: OrderInfoRequestModel, context: Context? = null) {
        countdownJob?.cancel()
        orderStatePollingJob?.cancel()
        _countdownState.value = ServiceCountdownState.ENDED
        
        // 停止前台服务
        context?.let { stopForegroundService(it) }
        
        // 使用unifiedOrderRepository结束服务（但保留图片数据）
        viewModelScope.launch {
            unifiedOrderRepository.endLocalService(orderInfoRequest.toOrderKey())
        }
        // 注意：不清除图片数据，因为订单可能需要重新开始
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
        orderStatePollingJob?.cancel()
    }
    
    /**
     * 启动订单状态轮询
     * 每5秒查询一次订单状态，如果订单不是"执行中"状态，则触发异常事件
     * 
     * @param orderKey 订单标识符
     */
    fun startOrderStatePolling(orderKey: OrderKey) {
        // 取消之前的轮询
        orderStatePollingJob?.cancel()
        
        orderStatePollingJob = viewModelScope.launch {
            while (true) {
                // 等待5秒
                delay(ORDER_STATE_POLLING_INTERVAL)
                
                // 如果服务已结束，停止轮询
                if (_countdownState.value == ServiceCountdownState.ENDED) {
                    break
                }
                
                // 查询订单状态
                when (val result = orderRepository.getOrderState(orderKey.orderId)) {
                    is ApiResult.Success -> {
                        val orderState = result.data
                        // 如果订单不是"执行中"状态，触发异常事件
                        if (!orderState.isInProgress()) {
                            _orderStateError.value = orderState
                            // 停止轮询
                            break
                        }
                    }
                    is ApiResult.Failure -> {
                        // 业务错误时不处理，继续轮询
                        println("查询订单状态失败: ${result.message}")
                    }
                    is ApiResult.Exception -> {
                        // 网络异常时不处理，继续轮询
                        println("查询订单状态异常: ${result.exception.message}")
                    }
                }
            }
        }
    }
    
    /**
     * 停止订单状态轮询
     */
    fun stopOrderStatePolling() {
        orderStatePollingJob?.cancel()
        orderStatePollingJob = null
    }
    
    /**
     * 清除订单状态异常事件
     * 在UI处理完弹窗后调用
     */
    fun clearOrderStateError() {
        _orderStateError.value = null
    }
    
    /**
     * 处理图片上传结果
     * @param orderRequest 订单信息请求模型
     * @param uploadResult 按ImageTaskType分组的ImageTask列表
     */
    fun handlePhotoUploadResult(orderRequest: OrderInfoRequestModel, uploadResult: Map<ImageTaskType, List<ImageTask>>) {
        // 保存上传的图片数据到状态中
        _uploadedImages.value = uploadResult
        // 图片数据已在PhotoProcessingViewModel中保存到ImageRepository
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
     * 从ImageRepository加载图片数据（用于页面恢复）
     * @param orderKey 订单标识符
     */
    fun loadUploadedImagesFromRepository(orderKey: OrderKey) {
        viewModelScope.launch {
            val images = imageRepository.getImagesByOrderId(orderKey)
            // 将OrderImageEntity转换为ImageTask并按类型分组
            val groupedImages = images
                .filter { entity ->
                    // 只处理支持的类型
                    val type = entity.getImageTypeEnum()
                    type == com.ytone.longcare.data.database.entity.ImageType.BEFORE_CARE ||
                    type == com.ytone.longcare.data.database.entity.ImageType.CENTER_CARE ||
                    type == com.ytone.longcare.data.database.entity.ImageType.AFTER_CARE
                }
                .groupBy { entity ->
                    when (entity.getImageTypeEnum()) {
                        com.ytone.longcare.data.database.entity.ImageType.BEFORE_CARE -> ImageTaskType.BEFORE_CARE
                        com.ytone.longcare.data.database.entity.ImageType.CENTER_CARE -> ImageTaskType.CENTER_CARE
                        com.ytone.longcare.data.database.entity.ImageType.AFTER_CARE -> ImageTaskType.AFTER_CARE
                        else -> ImageTaskType.BEFORE_CARE // 默认值，不应到达
                    }
                }
                .mapValues { (_, entities) ->
                    entities.map { entity ->
                        ImageTask(
                            id = entity.id.toString(),
                            originalUri = android.net.Uri.parse(entity.localUri),
                            taskType = when (entity.getImageTypeEnum()) {
                                com.ytone.longcare.data.database.entity.ImageType.BEFORE_CARE -> ImageTaskType.BEFORE_CARE
                                com.ytone.longcare.data.database.entity.ImageType.CENTER_CARE -> ImageTaskType.CENTER_CARE
                                com.ytone.longcare.data.database.entity.ImageType.AFTER_CARE -> ImageTaskType.AFTER_CARE
                                else -> ImageTaskType.BEFORE_CARE
                            }
                        )
                    }
                }
            if (groupedImages.isNotEmpty()) {
                _uploadedImages.value = groupedImages
            }
        }
    }
    
    /**
     * 检查是否有本地存储的图片数据
     * @param orderKey 订单标识符
     * @return 是否存在本地存储的图片数据
     */
    suspend fun hasLocalUploadedImages(orderKey: OrderKey): Boolean {
        return imageRepository.getImagesByOrderId(orderKey).isNotEmpty()
    }
    
    /**
     * 清除本地存储的图片数据（订单完成后调用）
     * @param orderKey 订单标识符
     */
    fun clearUploadedImagesFromLocal(orderKey: OrderKey) {
        viewModelScope.launch {
            imageRepository.deleteImagesByOrderId(orderKey)
            _uploadedImages.value = emptyMap()
        }
    }

    /**
     * 显示Toast提示
     * @param message 提示信息
     */
    fun showToast(message: String) {
        toastHelper.showShort(message)
    }
}