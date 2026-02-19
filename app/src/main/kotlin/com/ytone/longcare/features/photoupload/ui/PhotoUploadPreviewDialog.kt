package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.rememberAsyncImagePainter

@Composable
internal fun DeleteTaskButton(
    modifier: Modifier = Modifier,
    onRemove: () -> Unit
) {
    Box(
        modifier = modifier
            .padding(top = 2.dp, end = 2.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "操作图标",
            modifier = Modifier.size(12.dp),
            tint = Color.White
        )
    }
}

@Composable
fun ImagePreviewDialog(
    imageUri: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val windowInfo = LocalWindowInfo.current
    val density = LocalDensity.current
    val screenWidth = with(density) { windowInfo.containerSize.width.toDp().toPx() }
    val screenHeight = with(density) { windowInfo.containerSize.height.toDp().toPx() }

    val transformableState = rememberTransformableState { zoomChange, offsetChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 5f)
        val maxOffsetX = if (newScale > 1f) (screenWidth * (newScale - 1f)) / 2f else 0f
        val maxOffsetY = if (newScale > 1f) (screenHeight * (newScale - 1f)) / 2f else 0f

        val newOffset = if (newScale > 1f) {
            Offset(
                x = (offset.x + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX),
                y = (offset.y + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            )
        } else {
            Offset.Zero
        }

        scale = newScale
        offset = newOffset
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
                .transformable(state = transformableState),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = rememberAsyncImagePainter(imageUri),
                contentDescription = "预览图片",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    ),
                contentScale = ContentScale.Fit
            )
        }
    }
}
