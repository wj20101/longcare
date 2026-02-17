package com.ytone.longcare.common.location

import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.AmapApiKeyProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemConfigAmapApiKeyProvider @Inject constructor(
    private val systemConfigManager: SystemConfigManager
) : AmapApiKeyProvider {
    override suspend fun getAmapApiKey(): String? {
        return systemConfigManager.getThirdKey()?.gaoDeMapApiKey
    }
}
