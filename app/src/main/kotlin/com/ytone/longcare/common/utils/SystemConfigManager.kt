package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.ytone.longcare.api.response.SystemConfigModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.content.edit

/**
 * 系统配置管理器
 * 负责系统配置数据的存储和获取，支持内存缓存优化
 */
@Singleton
class SystemConfigManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "system_config_prefs"
        private const val KEY_SYSTEM_CONFIG = "system_config"
    }

    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    
    private val systemConfigAdapter = moshi.adapter(SystemConfigModel::class.java)
    
    // 内存缓存，使用volatile确保线程安全
    @Volatile
    private var cachedConfig: SystemConfigModel? = null
    
    // 缓存是否已初始化
    @Volatile
    private var cacheInitialized = false

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
     * 获取公司名称
     */
    fun getCompanyName(): String {
        return getSystemConfig()?.companyName ?: ""
    }

    /**
     * 获取最大上传图片数量
     */
    fun getMaxImgNum(): Int {
        return getSystemConfig()?.maxImgNum ?: 0
    }

    /**
     * 获取水印logo图片
     */
    fun getSyLogoImg(): String {
        return getSystemConfig()?.syLogoImg ?: ""
    }
}