package com.ytone.longcare.features.photoupload.utils

import android.content.Context
import coil3.ImageLoader
import com.ytone.longcare.common.utils.StorageSpaceUtils
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.common.utils.logE
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 图片缓存管理器
 * 提供缓存监控、清理和优化功能
 */
@Singleton
class ImageCacheManager @Inject constructor(
    private val context: Context,
    private val imageLoader: ImageLoader
) {
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitoringJob: Job? = null
    private companion object {
        const val CACHE_MONITOR_INTERVAL_MS = 60_000L
    }
    
    /**
     * 获取缓存统计信息
     */
    data class CacheStats(
        val memoryCacheSize: Long,
        val memoryCacheMaxSize: Long,
        val diskCacheSize: Long,
        val diskCacheMaxSize: Long,
        val memoryCacheHitCount: Long,
        val memoryCacheMissCount: Long
    )
    
    /**
     * 获取当前缓存统计信息
     */
    suspend fun getCacheStats(): CacheStats {
        val memoryCache = imageLoader.memoryCache
        val diskCache = imageLoader.diskCache
        
        return CacheStats(
            memoryCacheSize = memoryCache?.size ?: 0L,
            memoryCacheMaxSize = memoryCache?.maxSize ?: 0L,
            diskCacheSize = diskCache?.size ?: 0L,
            diskCacheMaxSize = diskCache?.maxSize ?: 0L,
            memoryCacheHitCount = 0L, // Coil 3.x 可能不直接提供这些统计
            memoryCacheMissCount = 0L
        )
    }
    
    /**
     * 清理内存缓存
     */
    fun clearMemoryCache() {
        cacheScope.launch {
            try {
                imageLoader.memoryCache?.clear()
                logD("内存缓存清理完成")
            } catch (e: Exception) {
                logE(message = "清理内存缓存失败", throwable = e)
            }
        }
    }
    
    /**
     * 清理磁盘缓存
     */
    fun clearDiskCache() {
        cacheScope.launch {
            try {
                imageLoader.diskCache?.clear()
                logD("磁盘缓存清理完成")
            } catch (e: Exception) {
                logE(message = "清理磁盘缓存失败", throwable = e)
            }
        }
    }
    
    /**
     * 清理所有缓存
     */
    fun clearAllCache() {
        clearMemoryCache()
        clearDiskCache()
    }
    
    /**
     * 智能缓存清理
     * 根据内存压力和存储空间自动清理缓存
     */
    fun smartCacheCleanup() {
        cacheScope.launch {
            try {
                val memoryInfo = android.app.ActivityManager.MemoryInfo()
                val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                activityManager.getMemoryInfo(memoryInfo)
                
                // 检查内存压力
                val memoryPressure = (memoryInfo.totalMem - memoryInfo.availMem).toFloat() / memoryInfo.totalMem
                
                // 检查存储空间
                val cacheDir = context.cacheDir
                val totalSpace = cacheDir.totalSpace
                val allocatableBytes = StorageSpaceUtils.getAllocatableBytes(context, cacheDir)
                val storageUsage = if (totalSpace > 0) {
                    ((totalSpace - allocatableBytes).toFloat() / totalSpace).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                logD("内存使用率: ${(memoryPressure * 100).toInt()}%, 存储使用率: ${(storageUsage * 100).toInt()}%")
                
                when {
                    // 内存压力很大（> 85%）
                    memoryPressure > 0.85f -> {
                        logD("内存压力过大，清理内存缓存")
                        imageLoader.memoryCache?.clear()
                    }
                    // 内存压力较大（> 70%）
                    memoryPressure > 0.70f -> {
                        logD("内存压力较大，清理部分内存缓存")
                        imageLoader.memoryCache?.trimToSize(imageLoader.memoryCache?.maxSize?.div(2) ?: 0L)
                    }
                }
                
                when {
                    // 存储空间紧张（> 90%）
                    storageUsage > 0.90f -> {
                        logD("存储空间紧张，清理磁盘缓存")
                        imageLoader.diskCache?.clear()
                    }
                    // 存储空间较紧张（> 80%）
                    storageUsage > 0.80f -> {
                        logD("存储空间较紧张，清理部分磁盘缓存")
                        // Coil 3.x 暂无稳定的“按目标大小裁剪”API，这里退化为全量清理。
                        imageLoader.diskCache?.clear()
                    }
                }
            } catch (e: Exception) {
                logE(message = "智能缓存清理失败", throwable = e)
            }
        }
    }
    
    /**
     * 预热缓存
     * 在应用空闲时预加载常用图片
     */
    fun warmUpCache(imageUrls: List<String>) {
        cacheScope.launch {
            try {
                logD("开始预热缓存，图片数量: ${imageUrls.size}")
                
                imageUrls.forEach { url ->
                    try {
                        val request = coil3.request.ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .networkCachePolicy(coil3.request.CachePolicy.ENABLED)
                            .build()
                        
                        imageLoader.execute(request)
                    } catch (e: Exception) {
                        logE(message = "预热缓存失败: $url", throwable = e)
                    }
                }
                
                logD("缓存预热完成")
            } catch (e: Exception) {
                logE(message = "缓存预热失败", throwable = e)
            }
        }
    }
    
    /**
     * 监控缓存性能
     * 定期输出缓存统计信息
     */
    fun startCacheMonitoring() {
        if (monitoringJob?.isActive == true) {
            logD("缓存监控已在运行，跳过重复启动")
            return
        }

        monitoringJob = cacheScope.launch {
            try {
                while (isActive) {
                    delay(CACHE_MONITOR_INTERVAL_MS)

                    val stats = getCacheStats()
                    val memoryUsagePercent = if (stats.memoryCacheMaxSize > 0) {
                        (stats.memoryCacheSize * 100 / stats.memoryCacheMaxSize).toInt()
                    } else 0

                    val diskUsagePercent = if (stats.diskCacheMaxSize > 0) {
                        (stats.diskCacheSize * 100 / stats.diskCacheMaxSize).toInt()
                    } else 0

                    logD("缓存监控 - 内存缓存: ${memoryUsagePercent}% (${formatBytes(stats.memoryCacheSize)}/${formatBytes(stats.memoryCacheMaxSize)}), 磁盘缓存: ${diskUsagePercent}% (${formatBytes(stats.diskCacheSize)}/${formatBytes(stats.diskCacheMaxSize)})")

                    // 如果缓存使用率过高，触发智能清理
                    if (memoryUsagePercent > 90 || diskUsagePercent > 90) {
                        smartCacheCleanup()
                    }
                }
            } catch (_: CancellationException) {
                logD("缓存监控任务已取消")
            } catch (e: Exception) {
                logE(message = "缓存监控失败", throwable = e)
            } finally {
                monitoringJob = null
            }
        }
    }

    /**
     * 停止缓存监控任务
     */
    fun stopCacheMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
    }
    
    /**
     * 格式化字节数为可读格式
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024 * 1024)}GB"
            bytes >= 1024 * 1024 -> "${bytes / (1024 * 1024)}MB"
            bytes >= 1024 -> "${bytes / 1024}KB"
            else -> "${bytes}B"
        }
    }
}
