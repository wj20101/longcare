package com.ytone.longcare.worker

import android.content.Context
import android.os.Environment
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytone.longcare.di.IoDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
    private val retrofit: Retrofit,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CoroutineWorker(context, workerParams) {

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
            workDataOf(KEY_ERROR to "URL不能为空")
        )
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure(
            workDataOf(KEY_ERROR to "文件名不能为空")
        )

        return withContext(ioDispatcher) {
            try {
                setProgressAsync(
                    workDataOf(KEY_PROGRESS to 0)
                )

                val downloadDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.filesDir
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                downloadWithRetrofit(url, fileName, downloadDir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "网络错误: ${e.message}")
                )
            } catch (e: Exception) {
                return@withContext Result.failure(
                    workDataOf(KEY_ERROR to "下载失败: ${e.message}")
                )
            }
        }
    }

    private suspend fun downloadWithRetrofit(
        url: String,
        fileName: String,
        downloadDir: File
    ): Result {
        val response = downloadApi.downloadFile(url)
        if (!response.isSuccessful) {
            return Result.failure(
                workDataOf(KEY_ERROR to "下载失败，HTTP状态码: ${response.code()}")
            )
        }

        val responseBody = response.body() ?: return Result.failure(
            workDataOf(KEY_ERROR to "响应体为空")
        )

        val file = File(downloadDir, fileName)
        val contentLength = responseBody.contentLength()
        val source = responseBody.source()
        val sink = file.sink().buffer()
        val buffer = Buffer()
        var bytesRead = 0L
        var lastProgress = -1
        var lastUpdateTime = 0L
        val updateInterval = 500L // 500ms 更新间隔

        source.use { input ->
            sink.use { output ->
                var read: Long
                while (input.read(buffer, 8192).also { read = it } != -1L) {
                    output.write(buffer, read)
                    bytesRead += read

                    if (contentLength > 0) {
                        val progress = (bytesRead * 100 / contentLength).toInt()
                        val currentTime = System.currentTimeMillis()
                        
                        // 只有当进度变化超过1%或时间间隔超过500ms时才更新
                        if (progress != lastProgress && 
                            (progress - lastProgress >= 1 || currentTime - lastUpdateTime >= updateInterval)) {
                            setProgressAsync(
                                workDataOf(KEY_PROGRESS to progress)
                            )
                            lastProgress = progress
                            lastUpdateTime = currentTime
                        }
                    }
                }
                output.flush()
            }
        }
        
        // 确保最后更新到100%
        if (contentLength > 0 && lastProgress < 100) {
            setProgressAsync(
                workDataOf(KEY_PROGRESS to 100)
            )
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
