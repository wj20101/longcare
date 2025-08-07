package com.ytone.longcare.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import okio.buffer
import okio.sink
import com.ytone.longcare.di.DefaultOkHttpClient
import java.io.File
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    @param:DefaultOkHttpClient private val okHttpClient: OkHttpClient
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val url = inputData.getString("url") ?: return Result.failure(
            workDataOf("error" to "URL is required")
        )
        val fileName = inputData.getString("fileName") ?: return Result.failure(
            workDataOf("error" to "File name is required")
        )

        return withContext(Dispatchers.IO) {
            try {
                setProgressAsync(
                    workDataOf("progress" to 0)
                )

                // 创建下载目录
                val downloadDir = File(applicationContext.getExternalFilesDir(null), "downloads")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()

                if (!response.isSuccessful) {
                     return@withContext Result.failure(
                         workDataOf("error" to "Download failed: ${response.code}")
                     )
                 }

                 val body = response.body

                val contentLength = body.contentLength()
                val file = File(downloadDir, fileName)
                val sink = file.sink().buffer()
                val source = body.source()
                val buffer = Buffer()
                var bytesRead = 0L

                source.use { input ->
                    sink.use { output ->
                        var read: Long
                        while (input.read(buffer, 8192).also { read = it } != -1L) {
                            output.write(buffer, read)
                            bytesRead += read

                            if (contentLength > 0) {
                                val progress = (bytesRead * 100 / contentLength).toInt()
                                setProgressAsync(
                                    workDataOf("progress" to progress)
                                )
                            }
                        }
                        output.flush()
                    }
                }

                Result.success(
                     workDataOf(
                         "filePath" to file.absolutePath,
                         "fileName" to fileName,
                         "fileSize" to bytesRead
                     )
                 )
             } catch (e: IOException) {
                 Result.failure(
                     workDataOf("error" to "Download failed: ${e.message}")
                 )
             } catch (e: Exception) {
                 Result.failure(
                     workDataOf("error" to "Unexpected error: ${e.message}")
                 )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(context: Context, params: WorkerParameters): DownloadWorker
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}