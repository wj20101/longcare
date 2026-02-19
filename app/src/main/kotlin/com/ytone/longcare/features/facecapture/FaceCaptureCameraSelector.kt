package com.ytone.longcare.features.facecapture

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.camera.core.CameraSelector
import androidx.core.content.getSystemService

/**
 * 获取可用的相机选择器。
 * 优先使用前置摄像头，如果不可用则回退到后置摄像头。
 */
internal fun getAvailableCameraSelector(context: Context): CameraSelector {
    return try {
        val cameraManager = context.getSystemService<CameraManager>()
            ?: return CameraSelector.DEFAULT_FRONT_CAMERA
        val cameraIds = cameraManager.cameraIdList

        val hasFrontCamera = cameraIds.any { id ->
            val characteristics = cameraManager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            facing == CameraCharacteristics.LENS_FACING_FRONT
        }

        if (hasFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            com.ytone.longcare.common.utils.KLogger.w("FaceCaptureScreen", "前置摄像头不可用，回退到后置摄像头")
            CameraSelector.DEFAULT_BACK_CAMERA
        }
    } catch (e: Exception) {
        com.ytone.longcare.common.utils.KLogger.e("FaceCaptureScreen", "检测相机失败: ${e.message}", e)
        CameraSelector.DEFAULT_FRONT_CAMERA
    }
}
