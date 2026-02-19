package com.ytone.longcare.network.processor

import com.ytone.longcare.model.ThirdKeyReturnModel

/**
 * thirdKeyStr 解密能力抽象。
 * 由 app 层提供具体实现，以避免 core/data 依赖 app 安全工具实现细节。
 */
interface ThirdKeyDecryptor {
    fun decryptThirdKeyStr(encryptedThirdKeyStr: String, aesKey: String): ThirdKeyReturnModel?
}

