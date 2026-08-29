package com.ytone.longcare.data.cos.repository

import android.content.Context
import com.tencent.cos.xml.CosXmlService
import com.tencent.cos.xml.CosXmlServiceConfig
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.data.session.SessionSecretProvider
import com.ytone.longcare.data.session.SessionOperationTracker
import com.ytone.longcare.domain.cos.repository.CosRepository
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.CosUploadResult
import com.ytone.longcare.model.UploadParams
import com.ytone.longcare.model.UploadProgress
import com.ytone.longcare.model.UploadTokenParamModel
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.model.toCosConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicReference
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

internal enum class SyncCredentialRefreshResult {
    SUCCESS,
    TIMED_OUT,
    FAILED,
}

internal enum class CredentialRefreshStrategy {
    SKIP_USE_CACHE,
    SKIP_NO_CACHE,
    REFRESH_SYNC,
}

internal fun resolveCredentialRefreshStrategy(
    isMainThread: Boolean,
    hasCachedConfig: Boolean,
): CredentialRefreshStrategy = when {
    isMainThread && hasCachedConfig -> CredentialRefreshStrategy.SKIP_USE_CACHE
    isMainThread -> CredentialRefreshStrategy.SKIP_NO_CACHE
    else -> CredentialRefreshStrategy.REFRESH_SYNC
}

internal suspend fun runSyncCredentialRefresh(
    timeoutMs: Long,
    refreshAction: suspend () -> Unit,
): SyncCredentialRefreshResult = try {
    withTimeout(timeoutMs) { refreshAction() }
    SyncCredentialRefreshResult.SUCCESS
} catch (_: TimeoutCancellationException) {
    SyncCredentialRefreshResult.TIMED_OUT
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (_: Exception) {
    SyncCredentialRefreshResult.FAILED
}

@Singleton
class CosRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val sessionSecretProvider: SessionSecretProvider,
) : CosRepository, SessionRuntimeCleanupHook {
    private data class ScopedService(
        val sessionFingerprint: String,
        val service: CosXmlService,
    )

    private val serviceMutex = Mutex()
    private val configMutex = Mutex()
    private val serviceRef = AtomicReference<ScopedService?>(null)
    private val configCache = CosConfigCache(TOKEN_REFRESH_THRESHOLD_SECONDS)
    private val operationTracker = SessionOperationTracker()

    private fun operationDelegate(sessionFingerprint: String) = CosObjectOperationDelegate(
        apiService = apiService,
        ioDispatcher = ioDispatcher,
        tag = TAG,
        getCosService = { getCosService(sessionFingerprint) },
        getValidCosConfig = { folderType -> getValidCosConfig(sessionFingerprint, folderType) },
        clearCache = { clearCache() },
    )

    private suspend fun getCosService(sessionFingerprint: String): CosXmlService {
        requireCurrentSession(sessionFingerprint)
        serviceRef.get()
            ?.takeIf { it.sessionFingerprint == sessionFingerprint }
            ?.let { return it.service }
        return serviceMutex.withLock {
            serviceRef.get()
                ?.takeIf { it.sessionFingerprint == sessionFingerprint }
                ?.let { return@withLock it.service }
            val config = getValidCosConfig(sessionFingerprint, CosConstants.DEFAULT_FOLDER_TYPE)
            createCosService(config, sessionFingerprint).also { service ->
                requireCurrentSession(sessionFingerprint)
                serviceRef.set(ScopedService(sessionFingerprint, service))
                logD(
                    "COS service initialized with bucket: ${config.bucket}, region: ${config.region}",
                    tag = TAG,
                )
            }
        }
    }

    private fun createCosService(config: CosConfig, sessionFingerprint: String): CosXmlService {
        val credentialProvider = CosDynamicCredentialProvider(
            defaultFolderType = CosConstants.DEFAULT_FOLDER_TYPE,
            getCachedConfig = { folderType -> configCache.getConfig(sessionFingerprint, folderType) },
            isConfigValid = { folderType -> configCache.isValid(sessionFingerprint, folderType) },
            refreshSync = { folderType -> refreshConfigSync(sessionFingerprint, folderType) },
            logTag = TAG,
        )
        val serviceConfig = CosXmlServiceConfig.Builder()
            .setRegion(config.region)
            .isHttps(true)
            .builder()
        return CosXmlService(context, serviceConfig, credentialProvider)
    }

    private suspend fun getValidCosConfig(
        sessionFingerprint: String,
        folderType: Int,
    ): CosConfig {
        requireCurrentSession(sessionFingerprint)
        if (configCache.isValid(sessionFingerprint, folderType)) {
            return configCache.getConfig(sessionFingerprint, folderType)
                ?: error("Valid COS config unexpectedly missing for folderType=$folderType")
        }
        return configMutex.withLock {
            requireCurrentSession(sessionFingerprint)
            if (configCache.isValid(sessionFingerprint, folderType)) {
                return@withLock configCache.getConfig(sessionFingerprint, folderType)
                    ?: error("Valid COS config unexpectedly missing for folderType=$folderType")
            }
            refreshCosConfig(sessionFingerprint, folderType)
        }
    }

    private suspend fun refreshCosConfig(
        sessionFingerprint: String,
        folderType: Int,
    ): CosConfig = withContext(ioDispatcher) {
        requireCurrentSession(sessionFingerprint)
        val cacheRevision = configCache.currentRevision()
        try {
            when (val response = apiService.getUploadToken(UploadTokenParamModel(folderType = folderType))) {
                is ApiResult.Success -> {
                    val config = response.data.toCosConfig()
                    requireCurrentSession(sessionFingerprint)
                    check(configCache.update(sessionFingerprint, folderType, config, cacheRevision)) {
                        "COS credential cache was revoked during refresh"
                    }
                    logD(
                        "COS config refreshed for folderType=$folderType, expires=${config.expiredTime}",
                        tag = TAG,
                    )
                    config
                }
                is ApiResult.Failure -> throw cosBackendFailure(
                    operation = "获取文件上传授权",
                    code = response.code,
                    message = response.message,
                )
                is ApiResult.Exception -> throw cosBackendException(
                    operation = "获取文件上传授权",
                    throwable = response.exception,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            logE("Failed to refresh COS config", tag = TAG, throwable = error)
            throw error
        }
    }

    private fun refreshConfigSync(sessionFingerprint: String, folderType: Int): Boolean {
        val result = runBlocking(ioDispatcher) {
            runSyncCredentialRefresh(SYNC_REFRESH_TIMEOUT_MS) {
                refreshCosConfig(sessionFingerprint, folderType)
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
        logD("COS session cache cleared", tag = TAG)
    }

    override suspend fun uploadFile(params: UploadParams): CosUploadResult =
        withSessionOperation { session -> operationDelegate(session).uploadFile(params) }

    override suspend fun uploadFileWithProgress(
        params: UploadParams,
        onProgress: (UploadProgress) -> Unit,
    ): CosUploadResult = withSessionOperation { session ->
        val guardedProgress: (UploadProgress) -> Unit = { progress ->
            if (sessionSecretProvider.activeSessionFingerprint() == session) onProgress(progress)
        }
        operationDelegate(session)
            .uploadFileWithProgress(params, guardedProgress)
    }

    override suspend fun getFileUrl(fileKey: String, folderType: Int?, fileSize: Long?): String =
        withSessionOperation { session ->
            operationDelegate(session).getFileUrl(fileKey, folderType, fileSize)
        }

    override suspend fun deleteFile(key: String): Boolean = withSessionOperation { session ->
        operationDelegate(session).deleteFile(key)
    }

    override suspend fun fileExists(key: String): Boolean = withSessionOperation { session ->
        operationDelegate(session).fileExists(key)
    }

    override suspend fun getFileSize(key: String): Long? = withSessionOperation { session ->
        operationDelegate(session).getFileSize(key)
    }

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        operationTracker.cancelAndJoin(
            "${identity.scopeKey.namespaceId().value}:${identity.sessionEpoch.value}"
        )
        clearCache()
    }

    private suspend fun <T> withSessionOperation(operation: suspend (String) -> T): T {
        val session = requireSessionFingerprint()
        return operationTracker.track(
            sessionFingerprint = session,
            validateSession = { requireCurrentSession(session) },
        ) {
            operation(session).also { requireCurrentSession(session) }
        }
    }

    private fun requireSessionFingerprint(): String =
        sessionSecretProvider.activeSessionFingerprint()
            ?: throw IllegalStateException("COS operation requires an active session")

    private fun requireCurrentSession(expected: String) {
        if (sessionSecretProvider.activeSessionFingerprint() != expected) {
            throw CancellationException("COS operation belongs to an expired session")
        }
    }

    private companion object {
        const val TAG = "CosRepositoryImpl"
        const val TOKEN_REFRESH_THRESHOLD_SECONDS = 300L
        const val SYNC_REFRESH_TIMEOUT_MS = 10_000L
    }
}
