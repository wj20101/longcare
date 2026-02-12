package com.ytone.longcare.features.photoupload.ui

import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner

@Composable
fun DelayTimerButton(
    currentMode: DelayMode,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.3f), CircleShape)
            .border(1.dp, Color.White, CircleShape)
            .size(48.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (currentMode == DelayMode.OFF) {
                Icon(
                    imageVector = Icons.Filled.TimerOff,
                    contentDescription = "延迟拍照: 关闭",
                    tint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(
                    text = "${currentMode.seconds}s",
                    color = if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
                    style = TextStyle(
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    ),
                )
            }
        }
    }
}

@Composable
fun ShutterButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCountingDown: Boolean = false,
) {
    val borderColor = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)
    val backgroundColor = if (isCountingDown) Color.Red else if (enabled) Color.White else Color.White.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(Color.Transparent)
            .border(4.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(backgroundColor),
        ) {
            if (isCountingDown) {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = "取消倒计时",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Circle,
                    contentDescription = "拍照",
                    modifier = Modifier.size(56.dp),
                    tint = backgroundColor,
                )
            }
        }
    }
}

@Composable
fun CameraSwitchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val iconTint = if (enabled) Color.White else Color.White.copy(alpha = 0.5f)

    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f)),
    ) {
        Icon(
            imageVector = Icons.Filled.Cameraswitch,
            contentDescription = "切换摄像头",
            tint = iconTint,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
fun CameraPreview(
    cameraController: LifecycleCameraController,
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            PreviewView(context).apply {
                controller = cameraController
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
                cameraController.bindToLifecycle(lifecycleOwner)
            }
        },
        modifier = modifier,
    )
}
