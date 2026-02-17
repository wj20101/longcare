package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp

@Composable
internal fun FaceDetectionIndicator(
    detected: Boolean,
    quality: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = detected,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(200.dp)
                .border(
                    width = 3.dp,
                    color = when {
                        quality > 0.8f -> Color.Green
                        quality > 0.6f -> Color.Yellow
                        else -> Color.Red
                    },
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }
}

@Composable
internal fun FaceThumbnail(
    bitmap: Bitmap,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(64.dp)
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "人脸照片",
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 3.dp else 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                    shape = CircleShape
                )
                .clickable(onClick = onClick)
        )

        if (isSelected) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "已选择",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 4.dp)
                    .size(20.dp)
                    .background(Color.White, CircleShape)
            )
        }

        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable { onDelete() }
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "删除照片",
                tint = Color.White,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}
