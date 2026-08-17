package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.ui.screen.ServiceHoursTag

@Composable
fun PhotoUploadSection(
    category: PhotoCategory,
    tasks: List<ImageTask>,
    isUploading: Boolean,
    isPhotoLimitLoaded: Boolean,
    maxPhotosPerCategory: Int?,
    onAddPhoto: () -> Unit,
    onRetryTask: (String) -> Unit,
    onRemoveTask: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPhotoLimitReached = maxPhotosPerCategory?.let { tasks.size >= it } == true

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(86.dp),
                modifier = Modifier
                    .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 18.dp)
                    .heightIn(max = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    AddPhotoButton(
                        onClick = onAddPhoto,
                        enabled = isPhotoLimitLoaded && !isUploading && !isPhotoLimitReached,
                        label = stringResource(
                            if (isPhotoLimitReached) {
                                R.string.photo_upload_limit_reached_short
                            } else {
                                R.string.photo_upload_add_photo
                            }
                        ),
                    )
                }
                items(tasks) { task ->
                    ImageTaskItem(
                        task = task,
                        onRetry = { onRetryTask(task.id) },
                        onRemove = { onRemoveTask(task.id) },
                        isUploading = isUploading
                    )
                }
            }
        }

        ServiceHoursTag(tagText = category.title, tagCategory = category.tagCategory)
    }
}
