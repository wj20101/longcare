package com.ytone.longcare.common.initializer

import android.content.Context
import androidx.startup.Initializer
import com.ytone.longcare.di.SystemConfigInitializerEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 系统配置初始化器
 * 使用Android App Startup在应用启动时自动获取系统配置
 */
class SystemConfigInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        // 获取Hilt EntryPoint
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            SystemConfigInitializerEntryPoint::class.java
        )
        
        val systemConfigManager = entryPoint.getSystemConfigManager()
        val apiService = entryPoint.getApiService()
        
        // 在后台线程中获取系统配置
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                // 检查是否已有缓存的配置
                val hasConfig = systemConfigManager.getSystemConfig() != null
                if (!hasConfig) {
                    // 调用API获取系统配置
                    val response = apiService.getSystemConfig()
                    if (response.isSuccess()) {
                        response.data?.let { config ->
                            systemConfigManager.saveSystemConfig(config)
                        }
                    }
                } else {
                    // 如果已有配置，可以选择在后台刷新
                    // 这里可以根据需要决定是否要定期刷新配置
                    try {
                        val response = apiService.getSystemConfig()
                        if (response.isSuccess()) {
                            response.data?.let { config ->
                                systemConfigManager.saveSystemConfig(config)
                            }
                        }
                    } catch (e: Exception) {
                        // 刷新失败时忽略，继续使用缓存的配置
                    }
                }
            } catch (e: Exception) {
                // 网络请求失败时，如果有缓存配置则继续使用
                // 这里可以添加日志记录
            }
        }
    }
    
    override fun dependencies(): List<Class<out Initializer<*>>> {
        // 返回空列表，表示没有依赖其他初始化器
        return emptyList()
    }
}