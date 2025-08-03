package com.ytone.longcare.features.servicecountdown.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.api.response.ServiceProjectM
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.ServiceTimeManager
import com.ytone.longcare.features.servicecountdown.ui.ServiceCountdownState
import com.ytone.longcare.features.photoupload.model.ImageTaskType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class ServiceCountdownViewModel @Inject constructor(
    private val toastHelper: ToastHelper,
    private val serviceTimeManager: ServiceTimeManager
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
    
    // 倒计时Job
    private var countdownJob: Job? = null
    
    // 已上传的图片数据
    private val _uploadedImages = MutableStateFlow<Map<ImageTaskType, List<String>>>(emptyMap())
    val uploadedImages: StateFlow<Map<ImageTaskType, List<String>>> = _uploadedImages.asStateFlow()
    
    /**
     * 根据项目列表设置倒计时时间
     * @param orderId 订单ID
     * @param projectList 所有项目列表
     * @param selectedProjectIds 选中的项目ID列表
     */
    fun setCountdownTimeFromProjects(
        orderId: Long,
        projectList: List<ServiceProjectM>, 
        selectedProjectIds: List<Int>
    ) {
        val totalMinutes = projectList
            .filter { it.projectId in selectedProjectIds }
            .sumOf { it.serviceTime }
        
        if (totalMinutes > 0) {
            // 获取或创建服务开始时间
            val serviceStartTime = serviceTimeManager.getOrCreateServiceStartTime(orderId)
            
            // 计算总服务时长（毫秒）
            val totalServiceTimeMillis = totalMinutes * 60 * 1000L
            
            // 计算已经过去的时间
            val elapsedTime = System.currentTimeMillis() - serviceStartTime
            
            // 计算剩余时间
            val remainingTime = maxOf(0L, totalServiceTimeMillis - elapsedTime)
            
            _remainingTimeMillis.value = remainingTime
            updateFormattedTime()
            
            // 如果剩余时间大于0，启动倒计时；否则设置为完成状态
            if (remainingTime > 0) {
                _countdownState.value = ServiceCountdownState.RUNNING
                startCountdown()
            } else {
                _countdownState.value = ServiceCountdownState.COMPLETED
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
            while (_remainingTimeMillis.value > 0) {
                // 更新格式化时间
                updateFormattedTime()
                
                // 延迟1秒
                delay(1000)
                
                // 减少剩余时间
                _remainingTimeMillis.value -= 1000
            }
            
            // 倒计时结束
            _remainingTimeMillis.value = 0
            updateFormattedTime()
            _countdownState.value = ServiceCountdownState.COMPLETED
        }
    }
    
    // 更新格式化时间
    private fun updateFormattedTime() {
        val hours = TimeUnit.MILLISECONDS.toHours(_remainingTimeMillis.value)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(_remainingTimeMillis.value) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(_remainingTimeMillis.value) % 60

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
     * @param orderId 订单ID，用于清除服务时间记录
     */
    fun endService(orderId: Long? = null) {
        countdownJob?.cancel()
        _countdownState.value = ServiceCountdownState.ENDED
        
        // 清除服务时间记录
        orderId?.let {
            serviceTimeManager.clearServiceTime(it)
        }
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
     * @param uploadResult 按ImageTaskType分组的图片URL列表
     */
    fun handlePhotoUploadResult(uploadResult: Map<ImageTaskType, List<String>>) {
        val beforeCareImages = uploadResult[ImageTaskType.BEFORE_CARE] ?: emptyList()
        val afterCareImages = uploadResult[ImageTaskType.AFTER_CARE] ?: emptyList()
        
        // 保存上传的图片数据到状态中
        _uploadedImages.value = uploadResult
        
        println("收到护理前图片: $beforeCareImages")
        println("收到护理后图片: $afterCareImages")
        println("已保存图片数据到ViewModel状态中")
    }
    
    /**
     * 获取当前已上传的图片数据
     * @return 按ImageTaskType分组的图片URL列表
     */
    fun getCurrentUploadedImages(): Map<ImageTaskType, List<String>> {
        return _uploadedImages.value
    }
    
    /**
     * 验证照片是否已上传
     * @return true表示护理前和护理后照片都已上传，false表示有照片未上传
     */
    fun validatePhotosUploaded(): Boolean {
        val uploadedImages = _uploadedImages.value
        val beforeCareImages = uploadedImages[ImageTaskType.BEFORE_CARE] ?: emptyList()
        val afterCareImages = uploadedImages[ImageTaskType.AFTER_CARE] ?: emptyList()
        
        return beforeCareImages.isNotEmpty() && afterCareImages.isNotEmpty()
    }
    
    /**
     * 显示Toast提示
     * @param message 提示信息
     */
    fun showToast(message: String) {
        toastHelper.showShort(message)
    }
}