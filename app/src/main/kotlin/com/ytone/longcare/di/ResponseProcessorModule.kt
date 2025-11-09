package com.ytone.longcare.di

import com.ytone.longcare.network.processor.ResponseProcessor
import com.ytone.longcare.network.processor.SystemConfigResponseProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * 响应处理器模块
 * 用于注册所有的ResponseProcessor实现
 * 
 * 使用Multibindings将所有处理器注入到Set中
 * 新增处理器只需在此添加@Binds @IntoSet方法即可
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ResponseProcessorModule {
    
    /**
     * 注册SystemConfig响应处理器
     */
    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSystemConfigProcessor(
        processor: SystemConfigResponseProcessor
    ): ResponseProcessor
    
    // 未来新增其他接口的处理器，在此添加：
    // @Binds
    // @IntoSet
    // @Singleton
    // abstract fun bindXxxProcessor(
    //     processor: XxxResponseProcessor
    // ): ResponseProcessor
}
