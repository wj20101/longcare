package com.ytone.longcare.debug

import com.ytone.longcare.BuildConfig

/**
 * NFC测试功能配置
 * 控制NFC测试功能的开关，便于后期删除
 */
object NfcTestConfig {
    /**
     * 是否启用NFC测试功能
     * 发布时设为false，或直接删除相关代码
     */
    val ENABLE_NFC_TEST = BuildConfig.DEBUG
    
    /**
     * 测试功能标识
     */
    const val TEST_TAG = "NFC_TEST"
}
