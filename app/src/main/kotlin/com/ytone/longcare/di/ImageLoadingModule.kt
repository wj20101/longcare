package com.ytone.longcare.di

import android.content.Context
import android.os.Build
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.svg.SvgDecoder
import coil3.util.DebugLogger
import coil3.video.VideoFrameDecoder
import com.ytone.longcare.BuildConfig
import okio.Path.Companion.toPath
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageLoadingModule {

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(SvgDecoder.Factory())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(VideoFrameDecoder.Factory())
            }
            // 配置优化的内存缓存
            .memoryCache {
                MemoryCache.Builder()
                    // 根据设备性能动态调整内存缓存大小
                    .maxSizePercent(context, getOptimalMemoryCachePercent(context))
                    // 启用强引用缓存，提高缓存命中率
                    .strongReferencesEnabled(true)
                    // 设置弱引用缓存大小
                    .weakReferencesEnabled(true)
                    .build()
            }
            // 配置优化的磁盘缓存
            .diskCache {
                DiskCache.Builder()
                    // 设置缓存目录
                    .directory("${context.cacheDir.absolutePath}/image_cache".toPath())
                    // 根据设备存储空间动态调整磁盘缓存大小
                    .maxSizeBytes(getOptimalDiskCacheSize(context))
                    // 设置缓存清理策略
                    .cleanupDispatcher(kotlinx.coroutines.Dispatchers.IO)
                    .build()
            }
            // 启用交叉淡入动画
            .crossfade(true)
            // Add a logger for debug builds
            .apply {
                if (BuildConfig.DEBUG) {
                    logger(DebugLogger())
                }
            }
            .build()
    }
    
    /**
     * 根据设备性能获取最优的内存缓存百分比
     */
    private fun getOptimalMemoryCachePercent(context: Context): Double {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memoryInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        return when {
            // 高端设备（可用内存 > 4GB）
            memoryInfo.availMem > 4L * 1024 * 1024 * 1024 -> 0.30
            // 中端设备（可用内存 > 2GB）
            memoryInfo.availMem > 2L * 1024 * 1024 * 1024 -> 0.25
            // 低端设备
            else -> 0.15
        }
    }
    
    /**
     * 根据设备存储空间获取最优的磁盘缓存大小
     */
    private fun getOptimalDiskCacheSize(context: Context): Long {
        val cacheDir = context.cacheDir
        val availableSpace = cacheDir.usableSpace
        
        return when {
            // 存储空间充足（> 8GB）
            availableSpace > 8L * 1024 * 1024 * 1024 -> 200L * 1024 * 1024 // 200MB
            // 存储空间一般（> 4GB）
            availableSpace > 4L * 1024 * 1024 * 1024 -> 150L * 1024 * 1024 // 150MB
            // 存储空间紧张（> 2GB）
            availableSpace > 2L * 1024 * 1024 * 1024 -> 100L * 1024 * 1024 // 100MB
            // 存储空间非常紧张
            else -> 50L * 1024 * 1024 // 50MB
        }
    }
}
