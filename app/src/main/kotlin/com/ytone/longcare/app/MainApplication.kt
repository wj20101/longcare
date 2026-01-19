package com.ytone.longcare.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tencent.bugly.crashreport.CrashReport
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {

    // 如果你想让Coil全局使用Hilt提供的ImageLoader，
    // 你的Application类需要实现ImageLoaderFactory
    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager with custom configuration
        WorkManager.initialize(this, workManagerConfiguration)

        // 初始化Bugly
        val userStrategy = CrashReport.UserStrategy(this)
        CrashReport.initCrashReport(this, userStrategy)

    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoaderProvider.get()
}

