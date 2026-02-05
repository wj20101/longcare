package com.ytone.longcare.features.location.provider

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import com.ytone.longcare.features.location.manager.ContinuousAmapLocationManager
import com.ytone.longcare.features.location.manager.LocationStateManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 复合定位提供者
 * 根据策略选择合适的定位提供者，支持自动回退机制
 */
@Singleton
class CompositeLocationProvider @Inject constructor(
    private val locationStateManager: LocationStateManager,
    private val systemLocationProvider: SystemLocationProvider,
    private val continuousAmapLocationManager: ContinuousAmapLocationManager
) : LocationProvider {
    
    // 当前使用的定位策略，默认为自动模式
    private var currentStrategy = LocationStrategy.AUTO
    
    /**
     * 设置定位策略
     */
    fun setLocationStrategy(strategy: LocationStrategy) {
        currentStrategy = strategy
        logI("定位策略已切换为: $strategy")
    }
    
    /**
     * 获取当前定位策略
     */
    fun getCurrentStrategy(): LocationStrategy = currentStrategy
    
    override suspend fun getCurrentLocation(): LocationResult? {
        return getCurrentLocation(useCache = true)
    }

    /**
     * 开启主动式持续定位（预热）
     * 
     * 调用此方法将启动或保持持续定位引擎运行。建议在需要高频/快速定位的页面（如NFC页面）进入时调用，
     * 并在页面退出时取消收集Flow。
     * 
     * @return 位置流，收集此流即可保持定位引擎活跃
     */
    fun startProactiveLocation(): Flow<LocationResult> {
        logI(" CompositeLocationProvider: 开启主动预热 (Proactive Warm-up)")
        return continuousAmapLocationManager.startContinuousLocation()
            .onEach { 
                // 自动将预热获取到的位置存入缓存
                locationStateManager.recordLocationSuccess(it)
            }
    }

    /**
     * 获取当前位置（支持缓存策略）
     * @param useCache 是否优先使用缓存，默认为true
     * @param maxAgeMs 缓存最大有效期（毫秒），默认为30秒
     */
    suspend fun getCurrentLocation(useCache: Boolean = true, maxAgeMs: Long = 30_000L): LocationResult? {
        // 1. 尝试缓存
        if (useCache) {
            locationStateManager.getValidLocation(maxAgeMs)?.let { 
                logI("CompositeLocationProvider: 命中有效缓存位置")
                return it 
            }
        }

        // 2. 缓存无效或禁用缓存，执行原有策略（Realtime）
        val result = when (currentStrategy) {
            LocationStrategy.SYSTEM -> {
                logI("使用系统定位策略")
                systemLocationProvider.getCurrentLocation()
            }
            LocationStrategy.AMAP -> {
                logI("使用高德定位策略")
                continuousAmapLocationManager.getCurrentLocation()
            }
            LocationStrategy.AUTO -> {
                logI("使用自动定位策略（优先高德，失败时回退到系统）")
                getLocationWithFallback()
            }
        }

        // 3. 成功后更新缓存（虽然Service也在更新，但双重保障）
        if (result != null) {
            locationStateManager.recordLocationSuccess(result)
        }
        
        return result
    }
    
    /**
     * 自动定位策略实现：优先使用高德定位，失败时回退到系统定位
     */
    private suspend fun getLocationWithFallback(): LocationResult? {
        // 首先尝试高德定位
        try {
            logI("尝试使用高德定位...")
            val amapResult = continuousAmapLocationManager.getCurrentLocation()
            if (amapResult != null) {
                logI("高德定位成功")
                return amapResult
            }
        } catch (e: Exception) {
            logE("高德定位异常: ${e.message}")
        }
        
        // 高德定位失败，回退到系统定位
        logI("高德定位失败，回退到系统定位...")
        try {
            val systemResult = systemLocationProvider.getCurrentLocation()
            if (systemResult != null) {
                logI("系统定位成功")
                return systemResult
            } else {
                logE("系统定位也失败了")
            }
        } catch (e: Exception) {
            logE("系统定位异常: ${e.message}")
        }
        
        return null
    }
    
    override fun destroy() {
        systemLocationProvider.destroy()
        // AmapLocationManager 由 ContinuousAmapLocationManager 自行管理生命周期
        // continuousAmapLocationManager.destroy()
        logI("复合定位提供者已销毁")
    }
}