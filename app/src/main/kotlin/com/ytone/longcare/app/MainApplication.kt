package com.ytone.longcare.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.utils.LogConfig
import com.ytone.longcare.common.utils.LogFileConfig
import com.ytone.longcare.worker.UpdateWorker
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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

        initLogger()
        initCrashReportingIfNeeded()
        scheduleStartupWorkersAsync()
    }

    private fun initCrashReportingIfNeeded() {
        // Debug/instrumentation builds prioritize startup determinism over crash-report SDK bootstrap.
        if (BuildConfig.DEBUG) return
        val userStrategy = CrashReport.UserStrategy(this)
        CrashReport.initCrashReport(this, userStrategy)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoaderProvider.get()

    private fun scheduleStartupWorkersAsync() {
        appScope.launch {
            val updateWorkRequest = OneTimeWorkRequestBuilder<UpdateWorker>().build()
            WorkManager.getInstance(this@MainApplication).enqueueUniqueWork(
                STARTUP_UPDATE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                updateWorkRequest
            )
        }
    }

    private fun initLogger() {
        val logDirectory = File(filesDir, "logs")
        KLogger.init(
            LogConfig(
                enabled = BuildConfig.DEBUG,
                globalTag = "LongCare",
                logToFileEnabled = BuildConfig.DEBUG,
                logFileConfig = LogFileConfig(directoryPath = logDirectory.absolutePath),
                maskSensitiveInfo = true
            )
        )
    }

    private companion object {
        const val STARTUP_UPDATE_WORK_NAME = "startup_update_worker"
    }
}
