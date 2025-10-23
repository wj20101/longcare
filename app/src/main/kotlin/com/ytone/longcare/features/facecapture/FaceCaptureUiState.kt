package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap

/**
 * UI状态数据类，用于管理人脸捕获功能的状态
 * 
 * @param capturedFaces 已捕获的人脸图片列表
 * @param isProcessing 是否正在处理图像
 * @param userHint 给用户的提示信息
 * @param error 错误信息，null表示无错误
 * @param selectedFaceIndex 用户选择的人脸图片索引，-1表示未选择
 * @param isCapturing 是否正在捕获模式
 * @param faceDetected 是否检测到人脸
 * @param faceQuality 当前检测到的人脸质量分数 (0.0-1.0)
 */
data class FaceCaptureUiState(
    val capturedFaces: List<Bitmap> = emptyList(),
    val isProcessing: Boolean = false,
    val userHint: String = "请正对摄像头，保持面部光线充足",
    val error: String? = null,
    val selectedFaceIndex: Int = -1,
    val isCapturing: Boolean = true,
    val faceDetected: Boolean = false,
    val faceQuality: Float = 0f
) {
    /**
     * 是否已达到最大捕获数量
     */
    val isMaxCaptured: Boolean
        get() = capturedFaces.size >= MAX_FACES
    
    /**
     * 是否有已捕获的人脸
     */
    val hasCapturedFaces: Boolean
        get() = capturedFaces.isNotEmpty()
    
    /**
     * 是否有选中的人脸
     */
    val hasSelectedFace: Boolean
        get() = selectedFaceIndex >= 0 && selectedFaceIndex < capturedFaces.size
    
    /**
     * 获取选中的人脸图片
     */
    val selectedFace: Bitmap?
        get() = if (hasSelectedFace) capturedFaces[selectedFaceIndex] else null
    
    companion object {
        const val MAX_FACES = 8
    }
}