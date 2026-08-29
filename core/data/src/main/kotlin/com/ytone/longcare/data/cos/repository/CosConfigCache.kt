package com.ytone.longcare.data.cos.repository

import com.ytone.longcare.model.CosConfig
import com.ytone.longcare.model.UploadTokenResultModel
import com.ytone.longcare.model.toCosConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal class CosConfigCache(
    private val refreshThresholdSeconds: Long,
) {
    private data class ScopedFolder(
        val sessionFingerprint: String,
        val folderType: Int,
    )

    private val configMap = ConcurrentHashMap<ScopedFolder, CosConfig>()
    private val revision = AtomicLong()

    fun isValid(sessionFingerprint: String, folderType: Int): Boolean {
        val config = configMap[ScopedFolder(sessionFingerprint, folderType)] ?: return false
        return !config.isExpiringSoon(refreshThresholdSeconds)
    }

    fun getConfig(sessionFingerprint: String, folderType: Int): CosConfig? =
        configMap[ScopedFolder(sessionFingerprint, folderType)]

    fun currentRevision(): Long = revision.get()

    fun clear() {
        revision.incrementAndGet()
        configMap.clear()
    }

    fun clearForType(sessionFingerprint: String, folderType: Int) {
        configMap.remove(ScopedFolder(sessionFingerprint, folderType))
    }

    fun update(
        sessionFingerprint: String,
        folderType: Int,
        config: CosConfig,
        expectedRevision: Long = revision.get(),
    ): Boolean {
        if (revision.get() != expectedRevision) return false
        configMap[ScopedFolder(sessionFingerprint, folderType)] = config
        if (revision.get() == expectedRevision) return true
        configMap.remove(ScopedFolder(sessionFingerprint, folderType), config)
        return false
    }

    fun updateFromToken(
        sessionFingerprint: String,
        folderType: Int,
        token: UploadTokenResultModel,
        expectedRevision: Long = revision.get(),
    ): Boolean = update(sessionFingerprint, folderType, token.toCosConfig(), expectedRevision)
}
