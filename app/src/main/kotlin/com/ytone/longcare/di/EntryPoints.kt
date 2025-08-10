package com.ytone.longcare.di

import com.ytone.longcare.common.utils.FaceVerificationStatusManager
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.features.location.provider.CompositeLocationProvider
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
    fun faceVerificationStatusManager(): FaceVerificationStatusManager
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
interface NursingExecutionEntryPoint {
    fun faceVerificationStatusManager(): FaceVerificationStatusManager
}