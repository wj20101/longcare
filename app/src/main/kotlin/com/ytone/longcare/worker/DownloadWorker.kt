package com.ytone.longcare.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okio.Buffer
import okio.buffer
import okio.sink
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url
import java.io.File
import java.io.IOException

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val retrofit: Retrofit
) : CoroutineWorker(context, workerParams) {

    // Retrofit API 接口用于下载
    interface DownloadApi {
        @Streaming
        @GET
        suspend fun downloadFile(@Url url: String): Response<ResponseBody>
    }

    private val downloadApi: DownloadApi by lazy {
        retrofit.create(DownloadApi::class.java)
    }

    override suspend fun doWork(): Result {
        val url = inputData.getString(KEY_URL) ?: return Result.failure(
            workDataOf(KEY_ERROR to "URL is required")
        )
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure(
            workDataOf(KEY_ERROR to "File name is required")
        )

        return withContext(Dispatchers.IO) {
            try {
                setProgressAsync(
                    workDataOf(KEY_PROGRESS to 0)
                )

                // 创建下载目录
                val downloadDir = File(applicationContext.getExternalFilesDir(null), "downloads")
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                // 使用 Retrofit2 方案下载
                downloadWithRetrofit(url, fileName, downloadDir)
            } catch (e: IOException) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "Download failed: ${e.message}")
                )
            } catch (e: Exception) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "Unexpected error: ${e.message}")
                )
            }
        }
    }

    /**
     * 使用 Retrofit2 进行下载的方法
     */
    private suspend fun downloadWithRetrofit(
        url: String,
        fileName: String,
        downloadDir: File
    ): Result {
        val response = downloadApi.downloadFile(url)
        
        if (!response.isSuccessful) {
            return Result.failure(
                workDataOf(KEY_ERROR to "Download failed: ${response.code()}")
            )
        }

        val body = response.body() ?: return Result.failure(
            workDataOf(KEY_ERROR to "Response body is null")
        )

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
                            workDataOf(KEY_PROGRESS to progress)
                        )
                    }
                }
                output.flush()
            }
        }

        return Result.success(
            workDataOf(
                KEY_FILE_PATH to file.absolutePath,
                KEY_FILE_NAME to fileName,
                "fileSize" to bytesRead
            )
        )
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
    }
}