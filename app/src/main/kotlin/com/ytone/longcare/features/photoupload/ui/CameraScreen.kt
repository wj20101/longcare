package com.ytone.longcare.features.photoupload.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.vm.CameraViewModel
import com.ytone.longcare.model.WatermarkData

@Composable
fun CameraScreen(
    actions: CameraActions,
    watermarkData: WatermarkData,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    CameraPermissionGate(
        onPermissionGranted = {
            CameraContent(
                context = context,
                watermarkData = watermarkData,
                viewModel = viewModel,
                onImageCaptured = { file ->
                    val savedUri = Uri.fromFile(file)
                    actions.onImageCaptured(savedUri.toString())
                },
            )
        }
    )
}
