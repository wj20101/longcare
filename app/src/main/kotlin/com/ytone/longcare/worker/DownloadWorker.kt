package com.ytone.longcare.worker

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ytone.longcare.common.diagnostics.DiagnosticEventTracker
import com.ytone.longcare.R
import com.ytone.longcare.core.ui.R as CoreUiR
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.platform.update.ApkPackageVerifier
import com.ytone.longcare.platform.update.ApkVerificationResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val retrofit: Retrofit,
    private val apkPackageVerifier: ApkPackageVerifier,
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
        val rawUrl =
            inputData.getString(KEY_URL)
                ?: return Result.failure(
                    workDataOf(KEY_ERROR to applicationContext.getString(R.string.update_invalid_url))
                )
        val url = rawUrl.toHttpUrlOrNull()
            ?.takeIf { it.isHttps }
            ?.toString()
            ?: return Result.failure(
                workDataOf(KEY_ERROR to applicationContext.getString(R.string.update_https_required)),
            )
        val rawFileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure(
            workDataOf(KEY_ERROR to applicationContext.getString(R.string.update_file_name_required))
        )
        val fileName = sanitizeApkFileName(rawFileName)
        val expectedVersionCode = inputData.getLong(KEY_EXPECTED_VERSION_CODE, 0L)

        return withContext(ioDispatcher) {
            try {
                setProgress(workDataOf(KEY_PROGRESS to 0))

                val downloadDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: applicationContext.filesDir
                if (!downloadDir.exists()) {
                    downloadDir.mkdirs()
                }

                downloadWithRetrofit(
                    url = url,
                    fileName = fileName,
                    downloadDir = downloadDir,
                    expectedVersionCode = expectedVersionCode,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                DiagnosticEventTracker.trackError(
                    category = UPDATE_DIAGNOSTIC_CATEGORY,
                    event = "apk_download_io_exception",
                    description = "APK下载网络IO异常",
                    throwable = e,
                    extras = DiagnosticEventTracker.safeUrlExtras(url) + mapOf(
                        "fileName" to fileName,
                    ),
                )
                return@withContext if (runAttemptCount < MAX_RETRY_COUNT) {
                    Result.retry()
                } else {
                    Result.failure(
                        workDataOf(
                            KEY_ERROR to applicationContext.getString(
                                CoreUiR.string.common_network_error_retry,
                            ),
                        ),
                    )
                }
            } catch (e: Exception) {
                DiagnosticEventTracker.trackError(
                    category = UPDATE_DIAGNOSTIC_CATEGORY,
                    event = "apk_download_exception",
                    description = "APK下载过程异常",
                    throwable = e,
                    extras = DiagnosticEventTracker.safeUrlExtras(url) + mapOf(
                        "fileName" to fileName,
                    ),
                )
                return@withContext Result.failure(
                    workDataOf(
                        KEY_ERROR to applicationContext.getString(R.string.update_download_failed),
                    )
                )
            }
        }
    }

    private suspend fun downloadWithRetrofit(
        url: String,
        fileName: String,
        downloadDir: File,
        expectedVersionCode: Long,
    ): Result {
        val response = downloadApi.downloadFile(url)
        if (!response.isSuccessful) {
            DiagnosticEventTracker.trackError(
                category = UPDATE_DIAGNOSTIC_CATEGORY,
                event = "apk_download_http_failure",
                description = "APK下载HTTP状态失败",
                extras = DiagnosticEventTracker.safeUrlExtras(url) + mapOf(
                    "fileName" to fileName,
                    "httpCode" to response.code(),
                    "httpMessage" to response.message(),
                ),
            )
            return Result.failure(
                workDataOf(KEY_ERROR to applicationContext.getString(R.string.update_download_failed))
            )
        }

        val responseBody = response.body() ?: run {
            DiagnosticEventTracker.trackError(
                category = UPDATE_DIAGNOSTIC_CATEGORY,
                event = "apk_download_empty_body",
                description = "APK下载响应体为空",
                extras = DiagnosticEventTracker.safeUrlExtras(url) + mapOf(
                    "fileName" to fileName,
                    "httpCode" to response.code(),
                ),
            )
            return Result.failure(
                workDataOf(KEY_ERROR to applicationContext.getString(R.string.update_empty_response)),
            )
        }

        val finalFile = File(downloadDir, fileName)
        val temporaryFile = File(downloadDir, ".$fileName.$id.part")
        temporaryFile.delete()
        val contentLength = responseBody.contentLength()
        val source = responseBody.source()
        val sink = temporaryFile.sink().buffer()
        val buffer = Buffer()
        var bytesRead = 0L
        var lastProgress = -1
        var lastUpdateTime = 0L
        val updateInterval = 500L // 500ms 更新间隔

        try {
            source.use { input ->
                sink.use { output ->
                    var read: Long
                    while (input.read(buffer, 8192).also { read = it } != -1L) {
                        output.write(buffer, read)
                        bytesRead += read

                        if (contentLength > 0) {
                            val progress = (bytesRead * 100 / contentLength).toInt()
                            val currentTime = System.currentTimeMillis()

                            if (
                                progress != lastProgress &&
                                (progress - lastProgress >= 1 || currentTime - lastUpdateTime >= updateInterval)
                            ) {
                                setProgress(workDataOf(KEY_PROGRESS to progress))
                                lastProgress = progress
                                lastUpdateTime = currentTime
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (contentLength > 0L && bytesRead != contentLength) {
                return Result.failure(
                    workDataOf(
                        KEY_ERROR to applicationContext.getString(R.string.update_download_incomplete),
                    ),
                )
            }

            when (
                val verification = apkPackageVerifier.verify(
                    apkFile = temporaryFile,
                    expectedVersionCode = expectedVersionCode,
                )
            ) {
                is ApkVerificationResult.Invalid -> {
                    DiagnosticEventTracker.trackError(
                        category = UPDATE_DIAGNOSTIC_CATEGORY,
                        event = "apk_verification_failed",
                        description = "下载后的APK未通过安全校验",
                        extras = mapOf(
                            "reason" to verification.reason.name,
                            "fileName" to fileName,
                            "expectedVersionCode" to expectedVersionCode,
                        ),
                    )
                    return Result.failure(workDataOf(KEY_ERROR to verification.message))
                }

                is ApkVerificationResult.Valid -> publishVerifiedApk(temporaryFile, finalFile)
            }

            if (contentLength > 0 && lastProgress < 100) {
                setProgress(workDataOf(KEY_PROGRESS to 100))
            }

            return Result.success(
                workDataOf(
                    KEY_FILE_PATH to finalFile.absolutePath,
                    KEY_FILE_NAME to fileName,
                    KEY_FILE_SIZE to bytesRead,
                )
            )
        } finally {
            if (temporaryFile.exists()) temporaryFile.delete()
        }
    }

    companion object {
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_FILE_PATH = "filePath"
        const val KEY_PROGRESS = "progress"
        const val KEY_ERROR = "error"
        const val KEY_EXPECTED_VERSION_CODE = "expectedVersionCode"
        const val KEY_FILE_SIZE = "fileSize"
        private const val UPDATE_DIAGNOSTIC_CATEGORY = "app_update"
        private const val MAX_RETRY_COUNT = 2
    }
}

private fun publishVerifiedApk(temporaryFile: File, finalFile: File) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        moveAtomicallyOnApi26(temporaryFile, finalFile)
        return
    }

    if (finalFile.exists() && !finalFile.delete()) {
        throw IOException("无法替换旧安装包")
    }
    if (!temporaryFile.renameTo(finalFile)) {
        throw IOException("无法发布已校验安装包")
    }
}

@RequiresApi(Build.VERSION_CODES.O)
private fun moveAtomicallyOnApi26(temporaryFile: File, finalFile: File) {
    try {
        Files.move(
            temporaryFile.toPath(),
            finalFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(
            temporaryFile.toPath(),
            finalFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
}

internal fun sanitizeApkFileName(rawFileName: String): String {
    val leafName = File(rawFileName).name
    val sanitized = leafName
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
        .trimStart('.')
        .take(MAX_APK_FILE_NAME_LENGTH)
        .ifBlank { "longcare-update.apk" }
    return if (sanitized.endsWith(".apk", ignoreCase = true)) sanitized else "$sanitized.apk"
}

private const val MAX_APK_FILE_NAME_LENGTH = 120
