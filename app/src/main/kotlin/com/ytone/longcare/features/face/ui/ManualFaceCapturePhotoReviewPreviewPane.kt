package com.ytone.longcare.features.face.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R

@Composable
internal fun PhotoReviewPreviewPane(
    modifier: Modifier = Modifier,
    bitmap: Bitmap?,
    detectedFaces: List<DetectedFace>,
    selectedFaceIndex: Int?,
    isProcessingFaces: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = stringResource(R.string.face_verification_captured_photo_description),
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                detectedFaces.forEachIndexed { index, face ->
                    val isSelected = index == selectedFaceIndex
                    val color = if (isSelected) Color.Green else Color.Red
                    val strokeWidth = if (isSelected) 6.dp.toPx() else 3.dp.toPx()

                    drawRect(
                        color = color,
                        topLeft = Offset(
                            face.boundingBox.left.toFloat(),
                            face.boundingBox.top.toFloat()
                        ),
                        size = Size(
                            face.boundingBox.width().toFloat(),
                            face.boundingBox.height().toFloat()
                        ),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }

        if (isProcessingFaces) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Card {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.face_verification_detecting_face))
                    }
                }
            }
        }
    }
}
