package com.ytone.longcare.data.cos.repository

import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.UploadTokenResultModel
import com.ytone.longcare.model.toCosConfig
import java.util.concurrent.ConcurrentHashMap

internal class CosConfigCache(
    private val refreshThresholdSeconds: Long
) {
    private val configMap = ConcurrentHashMap<Int, CosConfig>()

    fun isValid(folderType: Int): Boolean {
        val config = configMap[folderType] ?: return false
        return !config.isExpiringSoon(refreshThresholdSeconds)
    }

    fun getConfig(folderType: Int): CosConfig? {
        return configMap[folderType]
    }

    fun clear() {
        configMap.clear()
    }

    fun clearForType(folderType: Int) {
        configMap.remove(folderType)
    }

    fun update(folderType: Int, config: CosConfig) {
        configMap[folderType] = config
    }

    fun updateFromToken(folderType: Int, token: UploadTokenResultModel) {
        configMap[folderType] = token.toCosConfig()
    }
}
