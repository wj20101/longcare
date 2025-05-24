package com.ytone.longcare.di

import com.ytone.longcare.data.repository.SampleRepositoryImpl
import com.ytone.longcare.domain.repository.SampleRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // 安装到应用级别的组件，表示 SampleRepository 在整个应用生命周期内是单例
object RepositoryModule {

    // 使用 @Provides 方法来提供 SampleRepository 的实例
    @Singleton // 如果希望 SampleRepository 是单例
    @Provides
    fun provideSampleRepository(): SampleRepository {
        // 在这里创建并返回 SampleRepository 的实例
        // 如果 SampleRepository 有构造函数参数，你需要在这里提供它们
        return SampleRepositoryImpl() // 假设 SampleRepository 有一个无参构造函数
    }
}