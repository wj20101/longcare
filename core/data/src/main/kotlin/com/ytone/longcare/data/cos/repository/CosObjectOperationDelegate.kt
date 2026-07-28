package com.ytone.longcare.data.cos.repository

import android.content.Context
import android.net.Uri
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.model.`object`.DeleteObjectRequest
import com.tencent.cos.xml.model.`object`.HeadObjectRequest
import com.tencent.cos.xml.model.`object`.PutObjectRequest
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.CosUtils
import com.ytone.longcare.common.utils.getFileSize
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.CosStorageFailureKind
import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.SaveFileParamModel
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.UploadProgress
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

    suspend fun uploadFile(params: UploadParams): CosUploadResult =
        uploadFileInternal(params, null)

    suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult = uploadFileInternal(params, onProgress)

    suspend fun deleteFile(key: String): Boolean = withContext(ioDispatcher) {
        try {
            executeCosOperationWithRetry(clearCache) {
                val service = getCosService()
                val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
                service.deleteObject(DeleteObjectRequest(config.bucket, key))
            }
            logD("File deleted successfully: $key", tag = tag)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            val failure = throwable.toCosStorageException()
            if (failure.kind == CosStorageFailureKind.NOT_FOUND) {
                false
            } else {
                logE("Failed to delete file: $key", tag = tag, throwable = failure)
                throw failure
            }
        }
    }

    suspend fun fileExists(key: String): Boolean = withContext(ioDispatcher) {
        try {
            executeCosOperationWithRetry(clearCache) {
                val service = getCosService()
                val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
                service.headObject(HeadObjectRequest(config.bucket, key))
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            val failure = throwable.toCosStorageException()
            if (failure.kind == CosStorageFailureKind.NOT_FOUND) {
                false
            } else {
                logE("Error checking file existence: $key", tag = tag, throwable = failure)
                throw failure
            }
        }
    }

    suspend fun getFileSize(key: String): Long? = withContext(ioDispatcher) {
        try {
            val result =
                executeCosOperationWithRetry(clearCache) {
                    val service = getCosService()
                    val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
                    service.headObject(HeadObjectRequest(config.bucket, key))
                }
            result.headers?.get("Content-Length")?.firstOrNull()?.toLongOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            val failure = throwable.toCosStorageException()
            if (failure.kind == CosStorageFailureKind.NOT_FOUND) {
                null
            } else {
                logE("Failed to get file size for: $key", tag = tag, throwable = failure)
                throw failure
            }
        }
    }

    private suspend fun uploadFileInternal(
        params: UploadParams,
        onProgress: ((UploadProgress) -> Unit)?
    ): CosUploadResult {
        var resolvedKey = params.key
        return try {
            withContext(ioDispatcher) {
                val fileUri = Uri.parse(params.fileUri)
                val config =
                    executeCosOperationWithRetry(clearCache) {
                        val service = getCosService()
                        val currentConfig = getValidCosConfig(params.folderType)
                        if (resolvedKey.isBlank()) {
                            resolvedKey =
                                CosUtils.generateFileKey(
                                    currentConfig.fileKeyPre,
                                    fileUri,
                                )
                        }
                        val request =
                            PutObjectRequest(
                                currentConfig.bucket,
                                resolvedKey,
                                fileUri,
                            )
                        params.contentType?.let {
                            request.setRequestHeaders("Content-Type", it, false)
                        }
                        onProgress?.let { callback ->
                            request.setProgressListener { complete, target ->
                                callback(UploadProgress(complete, target))
                            }
                        }
                        service.putObject(request)
                        currentConfig
                    }

                val uploadedParams = params.copy(key = resolvedKey)
                val privateUrl =
                    executeCosOperationWithRetry(
                        clearCache = {},
                        operation = { getPrivateUrl(uploadedParams) },
                    )
                CosUploadResult(
                    success = true,
                    key = resolvedKey,
                    bucket = config.bucket,
                    region = config.region,
                    url = privateUrl,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (throwable: Throwable) {
            val failure = throwable.toCosStorageException()
            logE("Upload failed for key: $resolvedKey", tag = tag, throwable = failure)
            CosUploadResult(
                success = false,
                key = resolvedKey,
                errorMessage = failure.message,
                errorCode = failure.errorCode,
            )
        }
    }

    private suspend fun getPrivateUrl(params: UploadParams): String {
        val fileSize = Uri.parse(params.fileUri).getFileSize(context)
        val saveFileParam =
            SaveFileParamModel(
                folderType = params.folderType,
                fileKey = params.key,
                fileSize = fileSize,
            )
        val url = apiService.getFileUrl(saveFileParam).requirePrivateCosUrl()
        logD("Private file URL obtained from backend", tag = tag)
        return url
    }
}
