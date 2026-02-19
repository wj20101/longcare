package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap

internal object FaceCaptureStateDelegate {

    fun canCaptureNewFace(
        state: FaceCaptureUiState,
        currentTime: Long,
        lastCaptureTime: Long,
        captureInterval: Long
    ): Boolean {
        return !state.isMaxCaptured &&
            state.isCapturing &&
            (currentTime - lastCaptureTime > captureInterval)
    }

    fun withCapturedFace(
        state: FaceCaptureUiState,
        faceBitmap: Bitmap,
        quality: Float
    ): FaceCaptureUiState {
        val updatedFaces = state.capturedFaces + faceBitmap
        return state.copy(
            capturedFaces = updatedFaces,
            userHint = generateHint(quality, updatedFaces.size),
            faceQuality = quality
        )
    }

    fun updateProcessing(state: FaceCaptureUiState, isProcessing: Boolean): FaceCaptureUiState {
        return state.copy(isProcessing = isProcessing)
    }

    fun updateUserHint(state: FaceCaptureUiState, hint: String): FaceCaptureUiState {
        return state.copy(userHint = hint)
    }

    fun updateFaceDetection(
        state: FaceCaptureUiState,
        detected: Boolean,
        quality: Float
    ): FaceCaptureUiState {
        return state.copy(faceDetected = detected, faceQuality = quality)
    }

    fun selectFace(state: FaceCaptureUiState, index: Int): FaceCaptureUiState? {
        if (index !in state.capturedFaces.indices) return null
        return state.copy(selectedFaceIndex = index, isCapturing = false)
    }

    fun cancelSelection(state: FaceCaptureUiState): FaceCaptureUiState {
        return state.copy(
            selectedFaceIndex = -1,
            isCapturing = true,
            userHint = DEFAULT_CAPTURE_HINT
        )
    }

    fun removeFace(state: FaceCaptureUiState, index: Int): FaceCaptureUiState? {
        if (index !in state.capturedFaces.indices) return null
        val updatedFaces = state.capturedFaces.toMutableList().apply { removeAt(index) }
        return state.copy(
            capturedFaces = updatedFaces,
            selectedFaceIndex = -1,
            isCapturing = true,
            userHint = generateHint(state.faceQuality, updatedFaces.size)
        )
    }

    fun setError(state: FaceCaptureUiState, error: String): FaceCaptureUiState {
        return state.copy(error = error)
    }

    fun clearError(state: FaceCaptureUiState): FaceCaptureUiState {
        return state.copy(error = null)
    }

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

    private const val DEFAULT_CAPTURE_HINT = "请正对摄像头，保持面部光线充足"
}
