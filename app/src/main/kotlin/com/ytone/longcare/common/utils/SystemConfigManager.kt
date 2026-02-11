package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.api.response.SystemConfigModel
import com.ytone.longcare.api.response.ThirdKeyReturnModel
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.network.ApiResult
import com.ytone.longcare.common.network.safeApiCall
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit
import com.ytone.longcare.di.ApplicationScope
import com.ytone.longcare.di.IoDispatcher
import com.ytone.longcare.domain.faceauth.model.FaceVerificationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 系统配置管理器
 * 负责系统配置数据的存储和获取，支持内存缓存优化
 */
@Singleton
class SystemConfigManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val moshi: Moshi,
    private val apiService: LongCareApiService,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val eventBus: AppEventBus
) {
    companion object {
        private const val PREFS_NAME = "system_config_prefs"
        private const val KEY_SYSTEM_CONFIG = "system_config"
    }

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val systemConfigAdapter = moshi.adapter(SystemConfigModel::class.java)
    private val thirdKeyAdapter = moshi.adapter(ThirdKeyReturnModel::class.java)
    
    // 内存缓存，使用volatile确保线程安全
    @Volatile
    private var cachedConfig: SystemConfigModel? = null
    
    // 缓存是否已初始化
    @Volatile
    private var cacheInitialized = false
    
    // 用于同步网络请求的互斥锁
    private val loadMutex = Mutex()

    /**
     * 保存系统配置
     */
    fun saveSystemConfig(config: SystemConfigModel) {
        val configJson = systemConfigAdapter.toJson(config)
        sharedPreferences.edit {
            putString(KEY_SYSTEM_CONFIG, configJson)
        }
        // 更新内存缓存
        cachedConfig = config
        cacheInitialized = true
    }

    /**
     * 获取系统配置
     * 优先从内存缓存读取，缓存未命中时从SharedPreferences读取
     */
    fun getSystemConfig(): SystemConfigModel? {
        // 如果缓存已初始化，直接返回缓存数据
        if (cacheInitialized) {
            return cachedConfig
        }
        
        // 从SharedPreferences读取并缓存
        val configJson = sharedPreferences.getString(KEY_SYSTEM_CONFIG, null)
        val config = configJson?.let { 
            try {
                systemConfigAdapter.fromJson(it)
            } catch (e: Exception) {
                null
            }
        }
        
        // 更新缓存
        cachedConfig = config
        cacheInitialized = true
        
        return config
    }

    /**
     * 清除系统配置
     */
    fun clearSystemConfig() {
        sharedPreferences.edit {
            remove(KEY_SYSTEM_CONFIG)
        }
        // 清除内存缓存
        cachedConfig = null
        cacheInitialized = false
    }

    /**
     * 检查是否有系统配置
     */
    fun hasSystemConfig(): Boolean {
        return sharedPreferences.contains(KEY_SYSTEM_CONFIG)
    }

    /**
     * 强制刷新缓存
     * 从SharedPreferences重新加载配置数据
     */
    fun refreshCache() {
        cacheInitialized = false
        getSystemConfig() // 触发重新加载
    }
    
    /**
     * 获取系统配置（懒加载）
     * 优先从内存缓存获取，如果不存在则通过网络加载并缓存
     */
    private suspend fun getSystemConfigLazy(): SystemConfigModel? {
        // 如果缓存已初始化，直接返回缓存数据
        if (cacheInitialized) {
            return cachedConfig
        }
        
        // 使用互斥锁确保只有一个协程执行网络请求
        return loadMutex.withLock {
            // 双重检查，防止多个协程同时进入
            if (cacheInitialized) {
                return@withLock cachedConfig
            }
            
            // 先尝试从SharedPreferences读取
            val configJson = sharedPreferences.getString(KEY_SYSTEM_CONFIG, null)
            val localConfig = configJson?.let { 
                try {
                    systemConfigAdapter.fromJson(it)
                } catch (e: Exception) {
                    null
                }
            }
            
            if (localConfig != null) {
                // 如果本地有缓存，先使用本地缓存
                cachedConfig = localConfig
                cacheInitialized = true

                // 在后台刷新缓存，避免阻塞首次读取。
                refreshSystemConfigInBackground()
                return@withLock localConfig
            } else {
                // 本地没有缓存，从网络加载
                try {
                    val result = safeApiCall(ioDispatcher, eventBus) { apiService.getSystemConfig() }
                    if (result is ApiResult.Success) {
                        cachedConfig = result.data
                        saveSystemConfig(result.data)
                        return@withLock result.data
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // 网络请求失败，返回null
                }
                null
            }
        }
    }

    private fun refreshSystemConfigInBackground() {
        applicationScope.launch {
            try {
                val result = safeApiCall(ioDispatcher, eventBus) { apiService.getSystemConfig() }
                if (result is ApiResult.Success) {
                    saveSystemConfig(result.data)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 静默处理后台刷新失败
            }
        }
    }
    
    /**
     * 获取公司名称
     */
    suspend fun getCompanyName(): String {
        return getSystemConfigLazy()?.companyName ?: ""
    }

    /**
     * 获取最大上传图片数量
     */
    suspend fun getMaxImgNum(): Int {
        return getSystemConfigLazy()?.maxImgNum ?: 0
    }

    /**
     * 获取水印logo图片
     */
    suspend fun getSyLogoImg(): String {
        return getSystemConfigLazy()?.syLogoImg ?: ""
    }

    /**
     * 获取全选状态配置
     */
    suspend fun getSelectServiceType(): Int {
        return getSystemConfigLazy()?.selectServiceType ?: 0
    }

    suspend fun getThirdKey(): ThirdKeyReturnModel? {
        val config = getSystemConfigLazy() ?: return null
        val str = config.thirdKeyStr
        if (str.isBlank()) return null
        return try {
            thirdKeyAdapter.fromJson(str)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend fun getFaceVerificationConfig(): FaceVerificationConfig? {
        val third = getThirdKey() ?: return null
        if (third.txFaceAppId.isBlank() || third.txFaceAppSecret.isBlank() || third.txFaceAppLicence.isBlank()) {
            return null
        }
        return FaceVerificationConfig(
            appId = third.txFaceAppId,
            secret = third.txFaceAppSecret,
            licence = third.txFaceAppLicence
        )
    }
    
    fun getThirdKeySync(): ThirdKeyReturnModel? {
        val config = getSystemConfig() ?: return null
        val str = config.thirdKeyStr
        if (str.isBlank()) return null
        return try { thirdKeyAdapter.fromJson(str) } catch (_: Exception) { null }
    }
}
