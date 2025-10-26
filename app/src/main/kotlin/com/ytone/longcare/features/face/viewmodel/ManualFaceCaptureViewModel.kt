package com.ytone.longcare.features.face.viewmodel

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ytone.longcare.features.face.detector.StaticImageFaceDetector
import com.ytone.longcare.features.face.ui.DetectedFace
import com.ytone.longcare.features.face.ui.ManualFaceCaptureState
import com.ytone.longcare.features.face.ui.ManualFaceCaptureUiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ManualFaceCaptureViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualFaceCaptureUiState())
    val uiState: StateFlow<ManualFaceCaptureUiState> = _uiState.asStateFlow()

    private val _currentState = MutableStateFlow<ManualFaceCaptureState>(ManualFaceCaptureState.Idle)
    val currentState: StateFlow<ManualFaceCaptureState> = _currentState.asStateFlow()

    private val faceDetector = StaticImageFaceDetector()

    /**
     * 设置相机权限状态
     */
    fun setCameraPermissionGranted(granted: Boolean) {
        _uiState.value = _uiState.value.copy(cameraPermissionGranted = granted)
        if (granted) {
            _currentState.value = ManualFaceCaptureState.CameraReady
        }
    }

    /**
     * 开始拍照
     */
    fun startCapture() {
        _currentState.value = ManualFaceCaptureState.CapturingPhoto
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )
    }

    /**
     * 处理拍照完成
     */
    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(
            capturedPhoto = bitmap,
            isLoading = false
        )
        _currentState.value = ManualFaceCaptureState.ProcessingFaces
        
        // 开始人脸检测
        detectFaces(bitmap)
    }

    /**
     * 检测人脸
     */
    private fun detectFaces(bitmap: Bitmap) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isProcessingFaces = true)
                
                val detectedFaces = withContext(Dispatchers.Default) {
                    faceDetector.detectFaces(bitmap)
                }
                
                _uiState.value = _uiState.value.copy(
                    detectedFaces = detectedFaces,
                    isProcessingFaces = false
                )
                
                when {
                    detectedFaces.isEmpty() -> {
                        _currentState.value = ManualFaceCaptureState.NoFacesDetected
                        _uiState.value = _uiState.value.copy(
                            errorMessage = "未检测到人脸，请重新拍照"
                        )
                    }
                    detectedFaces.size == 1 -> {
                        // 只有一张人脸，自动选择
                        selectFace(0)
                    }
                    else -> {
                        // 多张人脸，需要用户选择
                        _currentState.value = ManualFaceCaptureState.FacesDetected
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessingFaces = false,
                    errorMessage = "人脸检测失败: ${e.message}"
                )
                _currentState.value = ManualFaceCaptureState.Error(e.message ?: "未知错误")
            }
        }
    }

    /**
     * 选择人脸
     */
    fun selectFace(index: Int) {
        val faces = _uiState.value.detectedFaces
        if (index in faces.indices) {
            _uiState.value = _uiState.value.copy(selectedFaceIndex = index)
            _currentState.value = ManualFaceCaptureState.FaceSelected
            
            // 显示确认对话框
            showConfirmationDialog()
        }
    }

    /**
     * 显示确认对话框
     */
    private fun showConfirmationDialog() {
        _uiState.value = _uiState.value.copy(showConfirmationDialog = true)
    }

    /**
     * 隐藏确认对话框
     */
    fun hideConfirmationDialog() {
        _uiState.value = _uiState.value.copy(showConfirmationDialog = false)
    }

    /**
     * 确认选择的人脸
     */
    fun confirmSelectedFace() {
        val selectedIndex = _uiState.value.selectedFaceIndex
        val faces = _uiState.value.detectedFaces
        
        if (selectedIndex != null && selectedIndex in faces.indices) {
            val selectedFace = faces[selectedIndex]
            hideConfirmationDialog()
            saveFaceImage(selectedFace)
        }
    }

    /**
     * 取消选择，重新拍照
     */
    fun cancelAndRetake() {
        hideConfirmationDialog()
        resetState()
    }

    /**
     * 保存人脸图片
     */
    private fun saveFaceImage(face: DetectedFace) {
        viewModelScope.launch {
            try {
                _currentState.value = ManualFaceCaptureState.SavingFace
                _uiState.value = _uiState.value.copy(isLoading = true)
                
                val savedPath = withContext(Dispatchers.IO) {
                    saveBitmapToFile(face.croppedFace)
                }
                
                _uiState.value = _uiState.value.copy(
                    savedFaceImagePath = savedPath,
                    isLoading = false
                )
                _currentState.value = ManualFaceCaptureState.Success
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "保存人脸图片失败: ${e.message}"
                )
                _currentState.value = ManualFaceCaptureState.Error(e.message ?: "保存失败")
            }
        }
    }

    /**
     * 保存 Bitmap 到文件
     */
    private fun saveBitmapToFile(bitmap: Bitmap): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "face_capture_$timestamp.jpg"
        
        // 保存到应用私有目录
        val file = File(context.filesDir, "face_captures").apply {
            if (!exists()) mkdirs()
        }
        val imageFile = File(file, filename)
        
        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return imageFile.absolutePath
        } catch (e: IOException) {
            throw IOException("保存图片失败: ${e.message}")
        }
    }

    /**
     * 重置状态，重新开始
     */
    fun resetState() {
        _uiState.value = ManualFaceCaptureUiState(
            cameraPermissionGranted = _uiState.value.cameraPermissionGranted
        )
        _currentState.value = if (_uiState.value.cameraPermissionGranted) {
            ManualFaceCaptureState.CameraReady
        } else {
            ManualFaceCaptureState.Idle
        }
    }

    /**
     * 清除错误信息
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * 获取人脸质量评估
     */
    fun getFaceQualityHints(faceIndex: Int): List<String> {
        val faces = _uiState.value.detectedFaces
        val capturedPhoto = _uiState.value.capturedPhoto
        
        return if (faceIndex in faces.indices && capturedPhoto != null) {
            val face = faces[faceIndex]
            val qualityResult = faceDetector.evaluateFaceQuality(face, capturedPhoto)
            qualityResult.hints
        } else {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        faceDetector.release()
    }
}