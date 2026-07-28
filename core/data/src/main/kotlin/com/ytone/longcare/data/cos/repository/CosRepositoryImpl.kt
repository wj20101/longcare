package com.ytone.longcare.data.cos.repository

import android.content.Context
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.CosXmlServiceConfig
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.UploadProgress
import com.ytone.longcare.model.UploadTokenParamModel
import com.ytone.longcare.model.toCosConfig
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

internal enum class SyncCredentialRefreshResult {
    SUCCESS,
    TIMED_OUT,
    FAILED
}

internal enum class CredentialRefreshStrategy {
    SKIP_USE_CACHE,
    SKIP_NO_CACHE,
    REFRESH_SYNC
}

internal fun resolveCredentialRefreshStrategy(
    isMainThread: Boolean,
    hasCachedConfig: Boolean
): CredentialRefreshStrategy = when {
    isMainThread && hasCachedConfig -> CredentialRefreshStrategy.SKIP_USE_CACHE
    isMainThread -> CredentialRefreshStrategy.SKIP_NO_CACHE
    else -> CredentialRefreshStrategy.REFRESH_SYNC
}

internal suspend fun runSyncCredentialRefresh(
    timeoutMs: Long,
    refreshAction: suspend () -> Unit
): SyncCredentialRefreshResult = try {
    withTimeout(timeoutMs) { refreshAction() }
    SyncCredentialRefreshResult.SUCCESS
} catch (_: TimeoutCancellationException) {
    SyncCredentialRefreshResult.TIMED_OUT
} catch (_: Exception) {
    SyncCredentialRefreshResult.FAILED
}

@Singleton
class CosRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : CosRepository {

    companion object {
        private const val TAG = "CosRepositoryImpl"
        private const val TOKEN_REFRESH_THRESHOLD_SECONDS = 300L
        private const val SYNC_REFRESH_TIMEOUT_MS = 10_000L
    }

    private val serviceMutex = Mutex()
    private val configMutex = Mutex()
    private val serviceRef = AtomicReference<CosXmlService?>(null)
    private val configCache = CosConfigCache(TOKEN_REFRESH_THRESHOLD_SECONDS)

    private val objectOperationDelegate by lazy {
        CosObjectOperationDelegate(
            context = context,
            apiService = apiService,
            ioDispatcher = ioDispatcher,
            tag = TAG,
            getCosService = { getCosService() },
            getValidCosConfig = { folderType -> getValidCosConfig(folderType) },
            clearCache = { clearCache() }
        )
    }

    private suspend fun getCosService(): CosXmlService {
        serviceRef.get()?.let { return it }
        return serviceMutex.withLock {
            serviceRef.get()?.let { return@withLock it }
            val config = getValidCosConfig(CosConstants.DEFAULT_FOLDER_TYPE)
            createCosService(config).also {
                serviceRef.set(it)
                logD(
                    "COS service initialized with bucket: ${config.bucket}, region: ${config.region}",
                    tag = TAG
                )
            }
        }
    }

    private fun createCosService(config: CosConfig): CosXmlService {
        val credentialProvider = CosDynamicCredentialProvider(
            defaultFolderType = CosConstants.DEFAULT_FOLDER_TYPE,
            getCachedConfig = { folderType -> configCache.getConfig(folderType) },
            isConfigValid = { folderType -> configCache.isValid(folderType) },
            refreshSync = { folderType -> refreshConfigSync(folderType) },
            logTag = TAG
        )
        val serviceConfig = CosXmlServiceConfig.Builder()
            .setRegion(config.region)
            .isHttps(true)
            .builder()
        return CosXmlService(context, serviceConfig, credentialProvider)
    }

    private suspend fun getValidCosConfig(folderType: Int): CosConfig {
        if (configCache.isValid(folderType)) {
            return configCache.getConfig(folderType)
                ?: throw IllegalStateException("Config cache is valid but config is null for folderType: $folderType")
        }
        return configMutex.withLock {
            if (configCache.isValid(folderType)) {
                return@withLock configCache.getConfig(folderType)
                    ?: throw IllegalStateException("Config cache is valid but config is null for folderType: $folderType")
            }
            refreshCosConfig(folderType)
        }
    }

    private suspend fun refreshCosConfig(folderType: Int): CosConfig = withContext(ioDispatcher) {
        try {
            logD("Refreshing COS config...", tag = TAG)
            when (
                val response =
                    apiService.getUploadToken(
                        UploadTokenParamModel(folderType = folderType)
                    )
            ) {
                is ApiResult.Success -> {
                    val token = response.data
                    val config = token.toCosConfig()
                    configCache.update(folderType, config)
                    logD(
                        "COS config refreshed successfully for folderType: $folderType, expires at: ${config.expiredTime}",
                        tag = TAG
                    )
                    config
                }
                is ApiResult.Failure -> {
                    throw cosBackendFailure(
                        operation = "获取文件上传授权",
                        code = response.code,
                        message = response.message,
                    )
                }
                is ApiResult.Exception -> {
                    throw cosBackendException(
                        operation = "获取文件上传授权",
                        throwable = response.exception,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("Failed to refresh COS config", tag = TAG, throwable = e)
            throw e
        }
    }

    private fun refreshConfigSync(folderType: Int): Boolean {
        val result = runBlocking(ioDispatcher) {
            runSyncCredentialRefresh(SYNC_REFRESH_TIMEOUT_MS) {
                refreshCosConfig(folderType)
            }
        }
        return when (result) {
            SyncCredentialRefreshResult.SUCCESS -> true
            SyncCredentialRefreshResult.TIMED_OUT -> {
                logE("Synchronous credential refresh timed out after ${SYNC_REFRESH_TIMEOUT_MS}ms", tag = TAG)
                false
            }
            SyncCredentialRefreshResult.FAILED -> {
                logE("Failed to refresh credentials synchronously", tag = TAG)
                false
            }
        }
    }

    private suspend fun clearCache() {
        serviceMutex.withLock { serviceRef.set(null) }
        configMutex.withLock { configCache.clear() }
        logD("Cache cleared", tag = TAG)
    }

    override suspend fun uploadFile(params: UploadParams): CosUploadResult {
        return objectOperationDelegate.uploadFile(params)
    }

    override suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit
    ): CosUploadResult {
        return objectOperationDelegate.uploadFileWithProgress(params, onProgress)
    }

    override suspend fun deleteFile(key: String): Boolean {
        return objectOperationDelegate.deleteFile(key)
    }

    override suspend fun fileExists(key: String): Boolean {
        return objectOperationDelegate.fileExists(key)
    }

    override suspend fun getFileSize(key: String): Long? {
        return objectOperationDelegate.getFileSize(key)
    }
}
