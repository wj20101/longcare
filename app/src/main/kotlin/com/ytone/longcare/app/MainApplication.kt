package com.ytone.longcare.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.tencent.bugly.crashreport.CrashReport
import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.diagnostics.CrashReportGateway
import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.common.utils.LogConfig
import com.ytone.longcare.common.utils.LogFileConfig
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.integration.qlz.QlzSdkWindowInsetsCompat
import com.ytone.longcare.features.location.session.LocationSessionLifecycleObserver
import com.ytone.longcare.worker.StartupUpdateWorkObserver
import dagger.hilt.android.HiltAndroidApp
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import java.util.concurrent.atomic.AtomicBoolean

@HiltAndroidApp
class MainApplication : Application(), SingletonImageLoader.Factory, Configuration.Provider {
    @Inject
    lateinit var imageLoaderProvider: Provider<ImageLoader>

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var privacyConsentManager: PrivacyConsentManager

    @Inject
    lateinit var startupUpdateWorkObserver: StartupUpdateWorkObserver

    @Inject
    lateinit var locationSessionLifecycleObserver: LocationSessionLifecycleObserver

    private val postConsentInitDone = AtomicBoolean(false)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        initLogger()
        QlzSdkWindowInsetsCompat.register(this)
        locationSessionLifecycleObserver.start()

        // 只有用户已同意隐私政策才初始化 SDK 和调度 Worker
        if (privacyConsentManager.isPrivacyConsented) {
            performPostConsentInit()
        }
    }

    /**
     * 隐私同意后调用，初始化需要设备标识符的 SDK 和 Worker。
     * 首次同意时由 UI 层调用，后续启动由 onCreate 直接调用。
     */
    fun performPostConsentInit() {
        if (!postConsentInitDone.compareAndSet(false, true)) return
        initCrashReportingIfNeeded()
        scheduleStartupWorkers()
    }

    private fun initCrashReportingIfNeeded() {
        if (BuildConfig.DEBUG) return
        try {
            val userStrategy = CrashReport.UserStrategy(this)
            CrashReport.initCrashReport(this, userStrategy)
            CrashReportGateway.markInitialized()
        } catch (initializationFailure: Throwable) {
            logE(
                message = "Crash reporting initialization failed",
                throwable = initializationFailure,
            )
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = imageLoaderProvider.get()

    private fun scheduleStartupWorkers() {
        startupUpdateWorkObserver.enqueueLatestCheck()
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
}
