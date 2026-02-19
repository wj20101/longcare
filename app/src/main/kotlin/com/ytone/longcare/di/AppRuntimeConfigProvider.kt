package com.ytone.longcare.di

import com.ytone.longcare.BuildConfig
import com.ytone.longcare.common.config.RuntimeConfigProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppRuntimeConfigProvider @Inject constructor() : RuntimeConfigProvider {
    override val useMockData: Boolean
        get() = BuildConfig.USE_MOCK_DATA
    override val baseUrl: String
        get() = BuildConfig.BASE_URL
    override val isDebug: Boolean
        get() = BuildConfig.DEBUG
    override val publicKey: String
        get() = BuildConfig.PUBLIC_KEY
}
