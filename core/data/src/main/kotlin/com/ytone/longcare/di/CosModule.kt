package com.ytone.longcare.di

import com.ytone.longcare.data.cos.repository.CosRepositoryImpl
import com.ytone.longcare.domain.cos.repository.CosRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * COS相关的依赖注入模块
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CosModule {
    
    /**
     * 绑定COS Repository实现
     */
    @Binds
    @Singleton
    abstract fun bindCosRepository(
        cosRepositoryImpl: CosRepositoryImpl
    ): CosRepository
}