package com.ytone.longcare.common.utils

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UsbHostProbeModule {
    @Binds
    @Singleton
    abstract fun bindUsbHostProbeManager(
        impl: DefaultUsbHostProbeManager,
    ): UsbHostProbeManager
}
