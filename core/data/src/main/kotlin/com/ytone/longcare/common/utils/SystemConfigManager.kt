package com.ytone.longcare.common.utils

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.data.userstorage.UserStorageUnavailableException
import com.ytone.longcare.data.userstorage.UserStorageRegistry
import com.ytone.longcare.domain.faceauth.FaceVerificationConfigProvider
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import com.ytone.longcare.domain.system.ServicePhotoConfigProvider
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.NamespaceId
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel
import com.ytone.longcare.model.result.ApiResult
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val systemConfigKey = stringPreferencesKey("user_system_config_v1")

/** Current-user system configuration; no legacy/global storage fallback is permitted. */
@Singleton
class SystemConfigManager @Inject constructor(
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val moshi: Moshi,
    private val apiService: LongCareApiService,
    private val storageRegistry: UserStorageRegistry,
) : FaceVerificationConfigProvider, ServicePhotoConfigProvider, SessionRuntimeCleanupHook {
    private data class CacheIdentity(
        val namespaceId: NamespaceId,
        val generation: Long,
    )

    private data class CacheSnapshot(
        val identity: CacheIdentity,
        val initialized: Boolean,
        val config: SystemConfigModel?,
    )

    private val systemConfigAdapter = moshi.adapter(SystemConfigModel::class.java)
    private val thirdKeyAdapter = moshi.adapter(ThirdKeyReturnModel::class.java)
    private val cache = AtomicReference<CacheSnapshot?>(null)
    private val loadMutex = Mutex()

    /** Persists only to the currently leased user DataStore. */
    suspend fun saveSystemConfig(config: SystemConfigModel) {
        saveSystemConfig(storageRegistry.requireCurrentLease(), config)
    }

    /** Synchronous consumers may use only the current generation's already-loaded snapshot. */
    fun getSystemConfig(): SystemConfigModel? {
        val lease = runCatching { storageRegistry.requireCurrentLease() }.getOrNull() ?: return null
        return cache.get()
            ?.takeIf { it.identity == lease.cacheIdentity() && it.initialized }
            ?.config
    }

    suspend fun clearSystemConfig() {
        val lease = storageRegistry.requireCurrentLease()
        clearSystemConfig(lease)
    }

    /** Clears any same-user snapshot and forces a network-only refresh for the exact lease. */
    internal suspend fun forceRehydrate(lease: UserStorageLease): Boolean {
        clearSystemConfig(lease)
        return fetchAndPersist(lease) != null
    }

    suspend fun hasSystemConfig(): Boolean {
        val lease = storageRegistry.requireCurrentLease()
        val exists = storageRegistry.dataStore(lease).data.first()[systemConfigKey] != null
        storageRegistry.requireValid(lease)
        return exists
    }

    fun refreshCache() {
        cache.set(null)
        applicationScope.launch { getSystemConfigLazy() }
    }

    private suspend fun getSystemConfigLazy(): SystemConfigModel? {
        val initialLease = runCatching { storageRegistry.requireCurrentLease() }.getOrNull() ?: return null
        cache.get()
            ?.takeIf { it.identity == initialLease.cacheIdentity() && it.initialized }
            ?.let { return it.config }

        return loadMutex.withLock {
            val lease = runCatching { storageRegistry.requireCurrentLease() }.getOrNull() ?: return@withLock null
            cache.get()
                ?.takeIf { it.identity == lease.cacheIdentity() && it.initialized }
                ?.let { return@withLock it.config }

            val localConfig = readPersistedConfig(lease)
            if (localConfig != null) {
                cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = localConfig))
                refreshSystemConfigInBackground(lease)
                return@withLock localConfig
            }

            fetchAndPersist(lease)
        }
    }

    private suspend fun readPersistedConfig(lease: UserStorageLease): SystemConfigModel? {
        val json = storageRegistry.dataStore(lease).data.first()[systemConfigKey] ?: return null
        val config = try {
            systemConfigAdapter.fromJson(json)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
        storageRegistry.requireValid(lease)
        return config
    }

    private suspend fun fetchAndPersist(lease: UserStorageLease): SystemConfigModel? = try {
        when (val result = apiService.getSystemConfig()) {
            is ApiResult.Success -> {
                storageRegistry.requireValid(lease)
                saveSystemConfig(lease, result.data)
                result.data
            }
            is ApiResult.Exception -> {
                if (result.exception is CancellationException) throw result.exception
                storageRegistry.requireValid(lease)
                cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = null))
                null
            }
            is ApiResult.Failure -> {
                storageRegistry.requireValid(lease)
                cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = null))
                null
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Exception) {
        logE("加载系统配置失败", throwable = error)
        try {
            storageRegistry.requireValid(lease)
        } catch (_: UserStorageUnavailableException) {
            // The request completed after logout/account switch. The revoked generation must not
            // update the next user's cache, and a normal lifecycle race must not crash the app.
            return null
        }
        cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = null))
        null
    }

    private suspend fun saveSystemConfig(lease: UserStorageLease, config: SystemConfigModel) {
        storageRegistry.dataStore(lease).edit { it[systemConfigKey] = systemConfigAdapter.toJson(config) }
        storageRegistry.requireValid(lease)
        cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = config))
    }

    private suspend fun clearSystemConfig(lease: UserStorageLease) {
        storageRegistry.requireValid(lease)
        storageRegistry.dataStore(lease).edit { it.remove(systemConfigKey) }
        storageRegistry.requireValid(lease)
        cache.set(CacheSnapshot(lease.cacheIdentity(), initialized = true, config = null))
    }

    private fun refreshSystemConfigInBackground(lease: UserStorageLease) {
        applicationScope.launch {
            try {
                fetchAndPersist(lease)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                logE("后台刷新系统配置失败", throwable = error)
            }
        }
    }

    suspend fun getCompanyName(): String = getSystemConfigLazy()?.companyName ?: ""

    /** Fetches a current-session value; failure never falls back to another generation. */
    suspend fun refreshCompanyName(): String? {
        val lease = runCatching { storageRegistry.requireCurrentLease() }.getOrNull() ?: return null
        return fetchAndPersist(lease)?.companyName
    }

    override suspend fun getMaxServicePhotoCount(): Int = getSystemConfigLazy()?.maxImgNum ?: 0

    suspend fun getSyLogoImg(): String = getSystemConfigLazy()?.syLogoImg ?: ""

    suspend fun getSelectServiceType(): Int = getSystemConfigLazy()?.selectServiceType ?: 0

    suspend fun getThirdKey(): ThirdKeyReturnModel? = parseThirdKey(getSystemConfigLazy())

    override suspend fun getFaceVerificationConfig(): FaceVerificationConfig? {
        val third = getThirdKey() ?: return null
        if (third.txFaceAppId.isBlank() || third.txFaceAppSecret.isBlank() || third.txFaceAppLicence.isBlank()) {
            return null
        }
        return FaceVerificationConfig(
            appId = third.txFaceAppId,
            secret = third.txFaceAppSecret,
            licence = third.txFaceAppLicence,
        )
    }

    fun getThirdKeySync(): ThirdKeyReturnModel? = parseThirdKey(getSystemConfig())

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        cache.set(null)
    }

    private fun parseThirdKey(config: SystemConfigModel?): ThirdKeyReturnModel? {
        val json = config?.thirdKeyStr?.takeIf(String::isNotBlank) ?: return null
        return try {
            thirdKeyAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun UserStorageLease.cacheIdentity() = CacheIdentity(
        namespaceId = scopeKey.namespaceId(),
        generation = generation.value,
    )
}
