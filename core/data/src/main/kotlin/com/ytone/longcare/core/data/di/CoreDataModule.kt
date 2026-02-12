package com.ytone.longcare.core.data.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Core data 层专用 DI 模块。
 * 后续逐步承接从 app/data 下沉的绑定。
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule
