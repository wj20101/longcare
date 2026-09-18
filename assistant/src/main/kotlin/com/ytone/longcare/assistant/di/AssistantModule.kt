package com.ytone.longcare.assistant.di

import com.ytone.longcare.assistant.BuildConfig
import com.ytone.longcare.common.config.RuntimeConfigProvider
import com.ytone.longcare.common.network.FlavorInterceptorApplier
import com.ytone.longcare.core.common.di.ApplicationScope
import com.ytone.longcare.core.common.di.DefaultDispatcher
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.core.common.di.MainDispatcher
import com.ytone.longcare.domain.location.LocationFacade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AssistantModule {
    @Provides @Singleton
    fun runtimeConfig(): RuntimeConfigProvider = object : RuntimeConfigProvider {
        override val useMockData = false
        override val baseUrl = "https://careapi.ytone.cn"
        override val isDebug = BuildConfig.DEBUG
        override val publicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAk45Er/DSjJwRNhReRT+4lINV6GanR3FwNutADNBwVoNQgY33bM/adLN5ZDmb8CwCeRJ4iBdcIX0co+2cm169HSHtJvOHUm864UbT63BrxKtnJCR+GkmsB3dj7YMwDbYArg7ymGP3EhWsiqMPdnR15+4LYIfK3l74nOZqPIPp8XkUKbbvJeieyslBIVSux2eytUGQjY8EPTE7nOHbAh8boWhiekFKevmx24dQBLoOrKrpTIv4pNiFSPxWCdBayCXjyr3Vq6Eg+vEDYN1+sxXWAj4bo/91TIbGQzdPCcCiZUQ1d7EgBp1JJKAsTTzkd+CusSTVpmmz/uVwjOaEHNzqWwIDAQAB"
    }

    @Provides @Singleton
    fun flavorInterceptors(): FlavorInterceptorApplier = object : FlavorInterceptorApplier {
        override fun apply(builder: okhttp3.OkHttpClient.Builder, context: android.content.Context) = builder
    }

    @Provides @IoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO
    @Provides @MainDispatcher
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main
    @Provides @DefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
    @Provides @Singleton @ApplicationScope
    fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun locationFacade(impl: AssistantLocationFacade): LocationFacade = impl

    @Provides @Singleton
    fun locationReadiness(impl: AssistantLocationFacade): com.ytone.longcare.domain.location.LocationRuntimeReadiness = impl
}
