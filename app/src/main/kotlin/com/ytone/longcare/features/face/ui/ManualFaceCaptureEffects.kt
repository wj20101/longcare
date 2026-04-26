package com.ytone.longcare.features.face.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

@Composable
internal fun ManualFaceCaptureEffects(
    context: Context,
    currentState: ManualFaceCaptureState,
    savedFaceImagePath: String?,
    onSetCameraPermissionGranted: (Boolean) -> Unit,
    onFaceCaptured: (String) -> Unit
) {
    LaunchedEffect(Unit) {
        val permission = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        onSetCameraPermissionGranted(permission == PackageManager.PERMISSION_GRANTED)
    }

    LaunchedEffect(currentState, savedFaceImagePath) {
        if (currentState is ManualFaceCaptureState.Success && savedFaceImagePath != null) {
            onFaceCaptured(savedFaceImagePath)
        }
    }
}
