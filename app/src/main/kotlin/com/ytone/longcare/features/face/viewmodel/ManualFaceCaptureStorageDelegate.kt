package com.ytone.longcare.features.face.viewmodel

import android.content.Context
import android.graphics.Bitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

class ManualFaceCaptureStorageDelegate @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun saveBitmapToFile(bitmap: Bitmap): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "face_capture_$timestamp.jpg"

        val file = File(context.filesDir, "face_captures").apply {
            if (!exists()) mkdirs()
        }
        val imageFile = File(file, filename)

        try {
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            return imageFile.absolutePath
        } catch (e: IOException) {
            throw IOException("保存图片失败: ${e.message}")
        }
    }
}
