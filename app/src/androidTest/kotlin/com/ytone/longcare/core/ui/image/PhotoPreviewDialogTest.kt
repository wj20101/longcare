package com.ytone.longcare.core.ui.image

import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoPreviewDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun bitmapAndUriCallersShareTheSameDismissiblePreview() {
        val bitmap = createBitmap(32, 32).apply { eraseColor(Color.BLUE) }

        composeRule.setContent {
            var showPreview by remember { mutableStateOf(true) }
            if (showPreview) {
                PhotoPreviewDialog(
                    imageModel = bitmap,
                    onDismiss = { showPreview = false },
                )
            }
        }

        composeRule
            .onNodeWithContentDescription("预览图片")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("关闭图片预览")
            .assertIsDisplayed()
            .performClick()

        composeRule
            .onNodeWithContentDescription("预览图片")
            .assertDoesNotExist()
        bitmap.recycle()
    }
}
