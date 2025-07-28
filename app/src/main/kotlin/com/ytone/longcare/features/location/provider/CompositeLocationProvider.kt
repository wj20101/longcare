package com.ytone.longcare.features.location.provider

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 复合定位提供者
 * 根据策略选择合适的定位提供者，支持自动回退机制
 */
@Singleton
class CompositeLocationProvider @Inject constructor(
    private val systemLocationProvider: SystemLocationProvider,
    private val amapLocationProvider: AmapLocationProvider
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
        return when (currentStrategy) {
            LocationStrategy.SYSTEM -> {
                logI("使用系统定位策略")
                systemLocationProvider.getCurrentLocation()
            }
            LocationStrategy.AMAP -> {
                logI("使用高德定位策略")
                amapLocationProvider.getCurrentLocation()
            }
            LocationStrategy.AUTO -> {
                logI("使用自动定位策略（优先高德，失败时回退到系统）")
                getLocationWithFallback()
            }
        }
    }
    
    /**
     * 自动定位策略实现：优先使用高德定位，失败时回退到系统定位
     */
    private suspend fun getLocationWithFallback(): LocationResult? {
        // 首先尝试高德定位
        try {
            logI("尝试使用高德定位...")
            val amapResult = amapLocationProvider.getCurrentLocation()
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
        amapLocationProvider.destroy()
        logI("复合定位提供者已销毁")
    }
}