package com.ytone.longcare.data.cos.repository

import android.os.Looper
import com.tencent.qcloud.core.auth.QCloudCredentialProvider
import com.tencent.qcloud.core.auth.QCloudLifecycleCredentials
import com.tencent.qcloud.core.auth.SessionQCloudCredentials
import com.ytone.longcare.common.constants.CosConstants
import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logW
import kotlinx.coroutines.CancellationException

internal class CosDynamicCredentialProvider(
    private val defaultFolderType: Int = CosConstants.DEFAULT_FOLDER_TYPE,
    private val getCachedConfig: (Int) -> CosConfig?,
    private val isConfigValid: (Int) -> Boolean,
    private val refreshSync: (Int) -> Boolean,
    private val isMainThread: () -> Boolean = { Looper.myLooper() == Looper.getMainLooper() },
    private val logTag: String = "CosRepositoryImpl"
) : QCloudCredentialProvider {

    override fun getCredentials(): QCloudLifecycleCredentials? {
        return try {
            val cachedConfig = getCachedConfig(defaultFolderType)
            if (!isConfigValid(defaultFolderType)) {
                when (
                    resolveCredentialRefreshStrategy(
                        isMainThread = isMainThread(),
                        hasCachedConfig = cachedConfig != null
                    )
                ) {
                    CredentialRefreshStrategy.SKIP_USE_CACHE -> {
                        logW("Skip sync credential refresh on main thread, fallback to cached token", tag = logTag)
                    }
                    CredentialRefreshStrategy.SKIP_NO_CACHE -> {
                        logE(
                            "No cached credential available on main thread; skip blocking refresh to avoid ANR",
                            tag = logTag
                        )
                    }
                    CredentialRefreshStrategy.REFRESH_SYNC -> {
                        val refreshed = refreshSync(defaultFolderType)
                        if (!refreshed && cachedConfig != null) {
                            logW("Credential refresh failed, fallback to cached token", tag = logTag)
                        }
                    }
                }
            }

            getCachedConfig(defaultFolderType)?.let { config ->
                SessionQCloudCredentials(
                    config.tmpSecretId,
                    config.tmpSecretKey,
                    config.sessionToken,
                    config.startTime,
                    config.expiredTime
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (e: Exception) {
            logE("Failed to get credentials", tag = logTag, throwable = e)
            null
        }
    }

    override fun refresh() {
        val refreshed = refreshSync(defaultFolderType)
        if (refreshed) {
            logD("Credentials refreshed successfully", tag = logTag)
        } else {
            logE("Failed to refresh credentials", tag = logTag)
        }
    }
}
