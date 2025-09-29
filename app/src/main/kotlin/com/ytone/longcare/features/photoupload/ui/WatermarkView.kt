package com.ytone.longcare.features.photoupload.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ytone.longcare.R

@Composable
fun WatermarkView(watermarkLines: List<String>) {
    val context = LocalContext.current

    Box(modifier = Modifier.padding(16.dp)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val lineIndicatorWidth = 8f
            val lineIndicatorTextSpacing = 20f
            
            drawIntoCanvas { canvas ->
                val paint = Paint().asFrameworkPaint().apply {
                    isAntiAlias = true
                    color = android.graphics.Color.WHITE
                    strokeWidth = lineIndicatorWidth
                }
                canvas.nativeCanvas.drawLine(
                    0f,
                    0f,
                    0f,
                    size.height,
                    paint
                )
            }
        }

        Column(modifier = Modifier.padding(start = 28.dp)) {
            AsyncImage(
                model = R.drawable.app_watermark_image,
                contentDescription = "Watermark Logo"
            )

            Spacer(modifier = Modifier.height(16.dp))

            watermarkLines.forEach { line ->
                Text(
                    text = line,
                    color = Color.White,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
