package com.ytone.longcare.features.update.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.update.viewmodel.AppUpdateViewModel

@Composable
fun AppUpdateDialog(
    viewModel: AppUpdateViewModel,
    onDismiss: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val appVersionModel = uiState.appVersionModel ?: return
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, uiState.hasPendingInstall) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uiState.hasPendingInstall) {
                viewModel.checkPermissionAndInstall()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    AppUpdateDialogContent(
        appVersionModel = appVersionModel,
        isDownloading = uiState.isDownloading,
        downloadProgress = uiState.downloadProgress,
        onDismiss = onDismiss,
        onStartDownload = viewModel::startDownload,
        onCancelDownload = viewModel::cancelDownload
    )
}
