package com.ytone.longcare.features.photoupload.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ytone.longcare.common.utils.PermissionPurposeDialog
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
import com.ytone.longcare.common.utils.locationPermissionPurposeNotice
import com.ytone.longcare.common.utils.messageRes
import com.ytone.longcare.common.utils.rememberLocationPermissionLauncher
import com.ytone.longcare.core.ui.R as CoreUiR
import com.ytone.longcare.domain.location.LocationFailure
import com.ytone.longcare.feature.photoupload.R

@Composable
internal fun rememberCameraLocationFailureHandler(): (LocationFailure) -> Unit {
    val context = LocalContext.current
    var showPermissionNotice by remember { mutableStateOf(false) }
    val permission = rememberLocationPermissionLauncher(onPermissionGranted = {})
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    fun openSettings(intent: Intent) {
        try {
            settings.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) {
            Toast.makeText(context, CoreUiR.string.permission_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }
    if (showPermissionNotice) {
        PermissionPurposeDialog(
            notice = locationPermissionPurposeNotice(stringResource(R.string.camera_service_photo_purpose)),
            onConfirm = {
                showPermissionNotice = false
                permission.launch(UnifiedPermissionHelper.getLocationRequiredPermissions())
            },
            onDismiss = { showPermissionNotice = false },
            onOpenSettings = {
                showPermissionNotice = false
                openSettings(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            },
        )
    }
    return { failure ->
        when (failure) {
            LocationFailure.PERMISSION -> showPermissionNotice = true
            LocationFailure.SERVICE_DISABLED -> {
                Toast.makeText(context, failure.messageRes(), Toast.LENGTH_LONG).show()
                openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            else -> Toast.makeText(context, failure.messageRes(), Toast.LENGTH_LONG).show()
        }
    }
}
