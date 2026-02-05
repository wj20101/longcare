package com.ytone.longcare.di

import com.ytone.longcare.common.utils.NavigationHelper
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.data.repository.UnifiedOrderRepository
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * 统一管理所有的 EntryPoint 接口
 * 用于在非 Hilt 管理的类中获取依赖注入的实例
 */

@EntryPoint
@InstallIn(SingletonComponent::class)
interface IdentificationEntryPoint {
    fun unifiedOrderRepository(): UnifiedOrderRepository
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NfcManagerEntryPoint {
    fun nfcManager(): NfcManager
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NfcLocationEntryPoint {
    fun compositeLocationProvider(): CompositeLocationProvider
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SelectServiceEntryPoint {
    fun unifiedOrderRepository(): UnifiedOrderRepository
    fun navigationHelper(): NavigationHelper
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NursingExecutionEntryPoint {
    fun navigationHelper(): NavigationHelper
    fun toastHelper(): ToastHelper
    fun nfcTestHelper(): NfcTestHelper
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface MainDashboardEntryPoint {
    fun navigationHelper(): NavigationHelper
    fun toastHelper(): ToastHelper
    fun nfcTestHelper(): NfcTestHelper
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceCountdownEntryPoint {
    fun countdownNotificationManager(): CountdownNotificationManager
}