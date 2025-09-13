package com.ytone.longcare.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.NfcManager


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // Hilt 已经内置了 @ApplicationContext Context 的提供，通常不需要显式提供
    // 但如果想在模块内部使用或做进一步封装，可以这样做
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    // 提供协程 Dispatchers
    @Provides
    @Singleton
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    @MainDispatcher
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(
        @DefaultDispatcher defaultDispatcher: CoroutineDispatcher
    ): CoroutineScope {
        // 使用 SupervisorJob() 以确保一个子协程的失败不会影响其他子协程
        return CoroutineScope(SupervisorJob() + defaultDispatcher)
    }

    /**
     * 提供 LocationManager 的单例。
     */
    @Provides
    @Singleton
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager {
        return ContextCompat.getSystemService(context, LocationManager::class.java)
            ?: throw IllegalStateException("LocationManager not found")
    }

    /**
     * 提供 NotificationManager 的单例。
     */
    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager {
        return ContextCompat.getSystemService(context, NotificationManager::class.java)
            ?: throw IllegalStateException("NotificationManager not found")
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager {
        return WorkManager.getInstance(context)
    }

    /**
     * 提供 AlarmManager 的单例。
     */
    @Provides
    @Singleton
    fun provideAlarmManager(@ApplicationContext context: Context): AlarmManager {
        return ContextCompat.getSystemService(context, AlarmManager::class.java)
            ?: throw IllegalStateException("AlarmManager not found")
    }

    /**
     * 【测试功能】提供 NfcTestHelper 的单例 - 后期可删除整个方法
     */
    @Provides
    @Singleton
    fun provideNfcTestHelper(
        appEventBus: AppEventBus,
        toastHelper: ToastHelper,
        nfcManager: NfcManager
    ): NfcTestHelper {
        return NfcTestHelper(appEventBus, toastHelper, nfcManager)
    }
}