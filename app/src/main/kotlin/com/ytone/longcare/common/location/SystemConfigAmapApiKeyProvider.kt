package com.ytone.longcare.common.location

import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.core.common.di.IoDispatcher
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemConfigAmapApiKeyProvider @Inject constructor(
    private val systemConfigManager: SystemConfigManager,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : AmapApiKeyProvider {
    override suspend fun getAmapApiKey(): String? = withContext(ioDispatcher) {
        // Cold configuration reads may access disk and parse JSON; SDK construction stays on Main.
        systemConfigManager.getThirdKey()?.gaoDeMapApiKey
    }
}
