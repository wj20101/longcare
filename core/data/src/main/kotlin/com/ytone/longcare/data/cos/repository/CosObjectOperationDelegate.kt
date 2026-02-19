package com.ytone.longcare.data.cos.repository

import android.content.Context
import android.net.Uri
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.exception.CosXmlServiceException
import com.tencent.cos.xml.model.`object`.DeleteObjectRequest
import com.tencent.cos.xml.model.`object`.HeadObjectRequest
import com.tencent.cos.xml.model.`object`.PutObjectRequest
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.getFileSize
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.SaveFileParamModel
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.UploadProgress
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal class CosObjectOperationDelegate(
    private val context: Context,
    private val apiService: LongCareApiService,
    private val ioDispatcher: CoroutineDispatcher,
    private val tag: String,
    private val getCosService: suspend () -> CosXmlService,
    private val getValidCosConfig: suspend (Int) -> CosConfig,
    private val clearCache: suspend () -> Unit
) {

    suspend fun uploadFile(params: UploadParams): CosUploadResult {
        return executeWithRetry { uploadFileInternal(params, null) }
    }

    suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return executeWithRetry { uploadFileInternal(params, onProgress) }
    }

    suspend fun deleteFile(key: String): Boolean = withContext(ioDispatcher) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
            val request = DeleteObjectRequest(config.bucket, key)
            service.deleteObject(request)
            logD("File deleted successfully: $key", tag = tag)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Failed to delete file: $key", tag = tag, throwable = e)
            false
        }
    }

    suspend fun fileExists(key: String): Boolean = withContext(ioDispatcher) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
            val request = HeadObjectRequest(config.bucket, key)
            service.headObject(request)
            true
        } catch (e: CosXmlServiceException) {
            e.statusCode != 404
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Error checking file existence: $key", tag = tag, throwable = e)
            false
        }
    }

    suspend fun getFileSize(key: String): Long? = withContext(ioDispatcher) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
            val request = HeadObjectRequest(config.bucket, key)
            val result = service.headObject(request)
            result.headers?.get("Content-Length")?.firstOrNull()?.toLongOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Failed to get file size for: $key", tag = tag, throwable = e)
            null
        }
    }

    private suspend fun <T> executeWithRetry(operation: suspend () -> T): T {
        return try {
            operation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logW("Operation failed, attempting retry after cache clear", tag = tag, throwable = e)
            try {
                clearCache()
                operation()
            } catch (e: CancellationException) {
                throw e
            } catch (retryException: Exception) {
                logE("Operation failed after retry", tag = tag, throwable = retryException)
                throw retryException
            }
        }
    }

    private suspend fun uploadFileInternal(
        params: UploadParams,
        onProgress: ((UploadProgress) -> Unit)?
    ): CosUploadResult = withContext(ioDispatcher) {
        try {
            val service = getCosService()
            val config = getValidCosConfig(params.folderType)
            val fileUri = Uri.parse(params.fileUri)
            val key = params.key.takeIf { it.isNotBlank() } ?: CosUtils.generateFileKey(
                config.fileKeyPre,
                fileUri
            )
            val request = PutObjectRequest(config.bucket, key, fileUri)
            val newParams = params.copy(key = key)
            newParams.contentType?.let {
                request.setRequestHeaders("Content-Type", it, false)
            }
            onProgress?.let { callback ->
                request.setProgressListener { complete, target ->
                    callback(UploadProgress(complete, target))
                }
            }

            service.putObject(request)

            CosUploadResult(
                success = true,
                key = newParams.key,
                bucket = config.bucket,
                region = config.region,
                url = getPublicUrl(service, newParams, config)
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Upload failed for key: ${params.key}", tag = tag, throwable = e)
            CosUploadResult(
                success = false,
                key = params.key,
                errorMessage = "Upload failed: ${e.message}"
            )
        }
    }

    private suspend fun getPublicUrl(
        service: CosXmlService,
        params: UploadParams,
        config: CosConfig
    ): String {
        val fallbackUrl = service.getObjectUrl(config.bucket, config.region, params.key)
        logD("Using fallbackUrl candidate: $fallbackUrl", tag = "getPublicUrl")
        return try {
            val fileSize = Uri.parse(params.fileUri).getFileSize(context)
            val saveFileParam = SaveFileParamModel(
                folderType = params.folderType,
                fileKey = params.key,
                fileSize = fileSize
            )
            val response = apiService.getFileUrl(saveFileParam)
            if (response.isSuccess()) {
                logD("getFileUrl API returned url: ${response.data}", tag = "getPublicUrl")
                response.data ?: fallbackUrl
            } else {
                logW("Failed to get file URL from API, using fallback", tag = tag)
                fallbackUrl
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Error getting file URL from API, using fallback", tag = tag, throwable = e)
            fallbackUrl
        }
    }
}
