package com.ytone.longcare.features.face.viewmodel

import android.graphics.Bitmap
import com.ytone.longcare.features.face.detector.StaticImageFaceDetector
import com.ytone.longcare.features.face.ui.DetectedFace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class ManualFaceCaptureFacePipelineDelegate(
    private val faceDetector: StaticImageFaceDetector,
    private val storageDelegate: ManualFaceCaptureStorageDelegate,
    private val defaultDispatcher: CoroutineDispatcher,
    private val ioDispatcher: CoroutineDispatcher
) {

    suspend fun detectFaces(bitmap: Bitmap): List<DetectedFace> = withContext(defaultDispatcher) {
        faceDetector.detectFaces(bitmap)
    }

    suspend fun saveFaceImage(face: DetectedFace): String = withContext(ioDispatcher) {
        storageDelegate.saveBitmapToFile(face.croppedFace)
    }

    fun getFaceQualityHints(face: DetectedFace, capturedPhoto: Bitmap): List<String> =
        faceDetector.evaluateFaceQuality(face, capturedPhoto).hints

    fun release() {
        faceDetector.release()
    }
}
