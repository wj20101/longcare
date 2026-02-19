package com.ytone.longcare.features.facecapture

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalAnimationApi::class)
@Composable
internal fun FaceCaptureBottomPanel(
    uiState: FaceCaptureUiState,
    onThumbnailClick: (index: Int, bitmap: Bitmap, isSelected: Boolean) -> Unit,
    onDeleteFace: (Int) -> Unit,
    onConfirmSelectedFace: () -> Unit,
    onCancelSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = uiState.userHint,
            transitionSpec = {
                fadeIn(animationSpec = tween(300)) + slideInVertically() togetherWith
                    fadeOut(animationSpec = tween(300)) + slideOutVertically()
            },
            label = "hint_animation"
        ) { hint ->
            Text(
                text = hint,
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (uiState.hasCapturedFaces) {
            Text(
                text = "已捕获 ${uiState.capturedFaces.size}/${FaceCaptureUiState.MAX_FACES} 张照片",
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                itemsIndexed(uiState.capturedFaces) { index, bitmap ->
                    val isSelected = index == uiState.selectedFaceIndex
                    FaceThumbnail(
                        bitmap = bitmap,
                        isSelected = isSelected,
                        onClick = { onThumbnailClick(index, bitmap, isSelected) },
                        onDelete = { onDeleteFace(index) }
                    )
                }
            }

            Text(
                text = "提示: 单击选择, 再次单击预览, 点击右上角删除",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.hasSelectedFace) {
                Button(
                    onClick = onConfirmSelectedFace,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("确认选择")
                }

                OutlinedButton(
                    onClick = onCancelSelection,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    ),
                    border = BorderStroke(1.dp, Color.White)
                ) {
                    Text("重新选择")
                }
            } else if (uiState.isCapturing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "正在捕获",
                        tint = if (uiState.faceDetected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            Color.Gray
                        },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.faceDetected) "检测到人脸" else "寻找人脸中...",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
