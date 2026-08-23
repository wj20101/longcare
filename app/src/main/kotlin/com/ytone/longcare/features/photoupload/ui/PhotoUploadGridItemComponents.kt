package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.ytone.longcare.core.ui.image.PhotoPreviewDialog
import androidx.compose.ui.unit.dp
import coil3.compose.rememberAsyncImagePainter
import com.ytone.longcare.R
import com.ytone.longcare.common.utils.singleClick
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus

@Composable
fun AddPhotoButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    label: String? = null,
) {
    val lineColor = if (enabled) Color(0xFF2C87FE) else Color.Gray
    val alpha = if (enabled) 1f else 0.5f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .border(1.dp, color = lineColor, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = singleClick(onClick = onClick))
            .padding(8.dp)
            .graphicsLayer(alpha = alpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = stringResource(R.string.photo_upload_add_photo_description),
            tint = lineColor,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label ?: stringResource(R.string.photo_upload_add_photo),
            fontSize = 12.sp,
            color = lineColor
        )
    }
}

@Composable
private fun DeleteTaskButton(
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .padding(top = 2.dp, end = 2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = stringResource(R.string.photo_upload_delete_photo),
            modifier = Modifier.size(12.dp),
            tint = Color.White,
        )
    }
}

@Composable
fun ImageTaskItem(
    task: ImageTask,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    isUploading: Boolean = false
) {
    var showPreview by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
    ) {
        when (task.status) {
            ImageTaskStatus.PROCESSING -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color(0xFF2C87FE)
                    )
                }
            }

            ImageTaskStatus.SUCCESS -> {
                task.resultUri?.let { uri ->
                    Image(
                        painter = rememberAsyncImagePainter(uri),
                        contentDescription = stringResource(R.string.photo_upload_processed_photo),
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { showPreview = true },
                        contentScale = ContentScale.Crop
                    )
                }
                if (!isUploading) {
                    DeleteTaskButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onRemove = onRemove
                    )
                }
            }

            ImageTaskStatus.FAILED -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.common_retry),
                        tint = Color.Red,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(onClick = onRetry)
                    )
                    Text(
                        text = stringResource(R.string.photo_upload_tap_to_retry),
                        fontSize = 8.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }
                if (!isUploading) {
                    DeleteTaskButton(
                        modifier = Modifier.align(Alignment.TopEnd),
                        onRemove = onRemove
                    )
                }
            }
        }
    }

    if (showPreview && task.status == ImageTaskStatus.SUCCESS) {
        task.resultUri?.let { uri ->
            PhotoPreviewDialog(
                imageModel = uri,
                onDismiss = { showPreview = false }
            )
        }
    }
}
