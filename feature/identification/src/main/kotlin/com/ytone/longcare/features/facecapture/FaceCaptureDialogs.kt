package com.ytone.longcare.features.facecapture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ytone.longcare.feature.identification.R

@Composable
internal fun PermissionDeniedScreen(
    showSettingsAction: Boolean,
    onPermissionAction: () -> Unit,
    onNavigateBack: () -> Unit
) {
    CameraProblemScreen(
        title = stringResource(R.string.face_capture_camera_permission_title),
        message = if (showSettingsAction) {
            stringResource(R.string.face_capture_camera_permission_settings_message)
        } else {
            stringResource(R.string.face_capture_camera_permission_message)
        },
        actionLabel = if (showSettingsAction) {
            stringResource(R.string.face_capture_open_settings)
        } else {
            stringResource(R.string.face_capture_grant_permission)
        },
        onAction = onPermissionAction,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
internal fun CameraUnavailableScreen(
    onRetry: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    CameraProblemScreen(
        title = stringResource(R.string.face_capture_camera_unavailable_title),
        message = stringResource(R.string.face_capture_camera_unavailable_message),
        actionLabel = stringResource(R.string.face_capture_camera_retry),
        onAction = onRetry,
        onNavigateBack = onNavigateBack,
    )
}

@Composable
private fun CameraProblemScreen(
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    onNavigateBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(actionLabel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(onClick = onNavigateBack) {
            Text(stringResource(R.string.default_face_verification_back))
        }
    }
}
