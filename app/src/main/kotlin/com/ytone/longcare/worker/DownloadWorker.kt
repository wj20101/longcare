package com.ytone.longcare.worker

import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

class DownloadWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()

        return withContext(Dispatchers.IO) {
            try {
                val outputFile = File(applicationContext.externalCacheDir, fileName)
                URL(url).openStream().use { input ->
                    FileOutputStream(outputFile).use { output ->
                        input.copyTo(output)
                    }
                }
                val intent = Intent("android.intent.action.DOWNLOAD_COMPLETE").apply {
                    putExtra(KEY_FILE_PATH, outputFile.absolutePath)
                }
                applicationContext.sendBroadcast(intent)
                Result.success(workDataOf(KEY_FILE_PATH to outputFile.absolutePath))
            } catch (e: Exception) {
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_URL = "KEY_URL"
        const val KEY_FILE_NAME = "KEY_FILE_NAME"
        const val KEY_FILE_PATH = "KEY_FILE_PATH"
    }
}