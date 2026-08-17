package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskStatus
import com.ytone.longcare.model.ImageTaskType

@Preview
@Composable
fun PhotoUploadSectionPreview() {
    val tasks = listOf(
        ImageTask(
            id = "1",
            originalUri = "",
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.SUCCESS,
            resultUri = "content://media/picker/0/com.android.providers.media.photopicker/media/1000000033"
        ),
        ImageTask(
            id = "2",
            originalUri = "",
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.PROCESSING
        ),
        ImageTask(
            id = "3",
            originalUri = "",
            taskType = ImageTaskType.BEFORE_CARE,
            status = ImageTaskStatus.FAILED,
            errorMessage = "Upload failed"
        )
    )
    PhotoUploadSection(
        category = PhotoCategory.BEFORE_CARE,
        tasks = tasks,
        isUploading = false,
        isPhotoLimitLoaded = true,
        maxPhotosPerCategory = 9,
        onAddPhoto = {},
        onRetryTask = {},
        onRemoveTask = {}
    )
}

@Preview
@Composable
fun AddPhotoButtonPreview() {
    AddPhotoButton(onClick = {})
}

@Preview
@Composable
fun ImageTaskItemPreview() {
    val task = ImageTask(
        id = "1",
        originalUri = "",
        taskType = ImageTaskType.BEFORE_CARE
    )
    ImageTaskItem(task = task, onRetry = {}, onRemove = {})
}

@Preview
@Composable
fun ConfirmAndNextButtonPreview() {
    Column(modifier = Modifier.padding(16.dp)) {
        ConfirmAndNextButton(
            text = "Confirm & Next",
            enabled = true,
            isLoading = false,
            onClick = {}
        )
        Spacer(modifier = Modifier.height(16.dp))
        ConfirmAndNextButton(
            text = "上传中...",
            enabled = false,
            isLoading = true,
            onClick = {}
        )
    }
}
