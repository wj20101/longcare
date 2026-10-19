package com.ytone.longcare.common.utils

import com.squareup.moshi.Moshi
import com.ytone.longcare.model.SystemConfigModel
import com.ytone.longcare.model.ThirdKeyReturnModel

/** Allowlist persisted third-party configuration; never retain opaque or malformed payloads. */
internal class SystemConfigSanitizer(moshi: Moshi) {
    private val thirdKeyAdapter = moshi.adapter(ThirdKeyReturnModel::class.java)
    private val publicKeyAdapter = moshi.adapter(Map::class.java)

    fun sanitize(config: SystemConfigModel): SystemConfigModel {
        val third = try {
            thirdKeyAdapter.fromJson(config.thirdKeyStr)
        } catch (_: Exception) {
            null
        }
        val publicKeys = third?.let {
            publicKeyAdapter.toJson(
                mapOf(
                    "GaoDeMapApiKey" to it.gaoDeMapApiKey,
                    "TxFaceAppId" to it.txFaceAppId,
                    "TxFaceAppLicence" to it.txFaceAppLicence,
                )
            )
        }.orEmpty()
        return config.copy(thirdKeyStr = publicKeys)
    }
}
