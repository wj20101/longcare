package com.ytone.longcare.features.facecapture

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.common.utils.logE

/**
 * 人脸捕获功能的ViewModel
 * 使用现代化的StateFlow和协程进行状态管理，包含内存优化
 */
@HiltViewModel
class FaceCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(FaceCaptureUiState())
    val uiState: StateFlow<FaceCaptureUiState> = _uiState.asStateFlow()

    private var lastCaptureTime = 0L
    private val captureInterval = 1500L // 1.5秒捕获间隔，优化性能
    
    // 使用WeakReference优化内存管理
    private val bitmapCache = mutableListOf<WeakReference<Bitmap>>()

    /**
     * 处理捕获到的人脸图片
     * @param faceBitmap 人脸图片
     * @param quality 人脸质量分数 (0.0-1.0)
     */
    fun onFaceCaptured(faceBitmap: Bitmap, quality: Float) {
        viewModelScope.launch {
            val currentTime = System.currentTimeMillis()
            val currentState = _uiState.value
            
            // 检查是否可以捕获新的人脸
            if (!currentState.isMaxCaptured && 
                currentState.isCapturing &&
                (currentTime - lastCaptureTime > captureInterval)) {
                
                // 清理已回收的弱引用
                cleanupBitmapCache()
                
                // 保存照片到files目录
                val savedPath = saveFaceImageToFiles(faceBitmap)
                if (savedPath != null) {
                    logD("Face image saved to: $savedPath", tag = "FaceCaptureViewModel")
                }
                
                // 添加新的人脸图片
                val updatedFaces = currentState.capturedFaces + faceBitmap
                bitmapCache.add(WeakReference(faceBitmap))
                
                _uiState.value = currentState.copy(
                    capturedFaces = updatedFaces,
                    userHint = generateHint(quality, updatedFaces.size),
                    faceQuality = quality
                )
                
                lastCaptureTime = currentTime
            }
        }
    }
    
    /**
     * 更新处理状态
     * @param isProcessing 是否正在处理
     */
    fun updateProcessingState(isProcessing: Boolean) {
        _uiState.value = _uiState.value.copy(isProcessing = isProcessing)
    }
    
    /**
     * 更新用户提示信息
     * @param hint 提示信息
     */
    fun updateUserHint(hint: String) {
        _uiState.value = _uiState.value.copy(userHint = hint)
    }
    
    /**
     * 更新人脸检测状态
     * @param detected 是否检测到人脸
     * @param quality 人脸质量分数
     */
    fun updateFaceDetectionState(detected: Boolean, quality: Float = 0f) {
        _uiState.value = _uiState.value.copy(
            faceDetected = detected,
            faceQuality = quality
        )
    }
    
    /**
     * 选择人脸图片
     * @param index 选择的图片索引
     */
    fun selectFace(index: Int) {
        val currentState = _uiState.value
        if (index >= 0 && index < currentState.capturedFaces.size) {
            _uiState.value = currentState.copy(
                selectedFaceIndex = index,
                isCapturing = false
            )
        }
    }
    
    /**
     * 取消选择，重新开始捕获
     */
    fun cancelSelection() {
        _uiState.value = _uiState.value.copy(
            selectedFaceIndex = -1,
            isCapturing = true,
            userHint = "请正对摄像头，保持面部光线充足"
        )
    }
    
    /**
     * 删除指定的人脸图片
     * @param index 要删除的图片索引
     */
    fun removeFace(index: Int) {
        val currentState = _uiState.value
        if (index >= 0 && index < currentState.capturedFaces.size) {
            val updatedFaces = currentState.capturedFaces.toMutableList().apply {
                removeAt(index)
            }
            
            // 清理对应的弱引用
            if (index < bitmapCache.size) {
                bitmapCache[index].get()?.recycle()
                bitmapCache.removeAt(index)
            }
            
            _uiState.value = currentState.copy(
                capturedFaces = updatedFaces,
                selectedFaceIndex = -1,
                isCapturing = true,
                userHint = generateHint(currentState.faceQuality, updatedFaces.size)
            )
        }
    }
    
    /**
     * 清空所有捕获的人脸
     */
    fun clearAllFaces() {
        // 回收所有Bitmap
        bitmapCache.forEach { it.get()?.recycle() }
        bitmapCache.clear()
        
        _uiState.value = FaceCaptureUiState()
    }
    
    /**
     * 设置错误信息
     * @param error 错误信息
     */
    fun setError(error: String) {
        _uiState.value = _uiState.value.copy(error = error)
    }
    
    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * 根据质量和捕获数量生成用户提示
     * @param quality 人脸质量分数
     * @param capturedCount 已捕获数量
     * @return 提示信息
     */
    private fun generateHint(quality: Float, capturedCount: Int): String {
        return when {
            capturedCount >= FaceCaptureUiState.MAX_FACES -> 
                "已捕获足够照片，请选择一张最满意的"
            quality < 0.6f -> 
                "请保持面部正对摄像头"
            quality < 0.8f -> 
                "请保持光线充足，避免阴影"
            else -> 
                "很好！继续保持姿势 ($capturedCount/${FaceCaptureUiState.MAX_FACES})"
        }
    }
    
    /**
     * 保存人脸图片到app的files目录
     * @param bitmap 要保存的图片
     * @return 保存成功返回文件路径，失败返回null
     */
    private suspend fun saveFaceImageToFiles(bitmap: Bitmap): String? {
        return withContext(Dispatchers.IO) {
            try {
                // 创建files目录下的face_capture子目录
                val faceCaptureDir = File(context.filesDir, "face_capture")
                if (!faceCaptureDir.exists()) {
                    faceCaptureDir.mkdirs()
                }
                
                // 生成唯一的文件名
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
                val fileName = "face_$timestamp.jpg"
                val file = File(faceCaptureDir, fileName)
                
                // 保存图片
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                logI("Face image saved successfully: ${file.absolutePath}", tag = "FaceCaptureViewModel")
                file.absolutePath
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logE("Failed to save face image", tag = "FaceCaptureViewModel", throwable = e)
                null
            }
        }
    }
    
    /**
     * 获取已保存的人脸图片列表
     * @return 文件路径列表
     */
    fun getSavedFaceImages(): List<String> {
        return try {
            val faceCaptureDir = File(context.filesDir, "face_capture")
            if (faceCaptureDir.exists()) {
                faceCaptureDir.listFiles { file ->
                    file.isFile && file.name.endsWith(".jpg")
                }?.map { it.absolutePath } ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            logE("Failed to get saved face images", tag = "FaceCaptureViewModel", throwable = e)
            emptyList()
        }
    }
    
    /**
     * 清理已回收的弱引用
     */
    private fun cleanupBitmapCache() {
        bitmapCache.removeAll { it.get() == null }
    }
    
    override fun onCleared() {
        super.onCleared()
        // 清理资源
        bitmapCache.forEach { it.get()?.recycle() }
        bitmapCache.clear()
    }
}
