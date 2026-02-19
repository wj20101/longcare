package com.ytone.longcare.network.processor

import com.ytone.longcare.common.utils.ThirdKeyDecryptUtils
import com.ytone.longcare.model.ThirdKeyReturnModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThirdKeyDecryptorImpl @Inject constructor() : ThirdKeyDecryptor {
    override fun decryptThirdKeyStr(
        encryptedThirdKeyStr: String,
        aesKey: String,
    ): ThirdKeyReturnModel? = ThirdKeyDecryptUtils.decryptThirdKeyStr(encryptedThirdKeyStr, aesKey)
}

