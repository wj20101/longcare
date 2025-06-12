package com.ytone.longcare.data.storage

import androidx.datastore.preferences.core.byteArrayPreferencesKey

/**
 * 一个单例对象，用于集中管理应用中所有 DataStore Preferences 的 Key。
 * 这样做可以避免在代码中散落“魔法字符串”，并确保 Key 的唯一性。
 */
object DataStoreKeys {

    // --- 应用级别 (App-Level) 的 Key ---
    // (用于我们之前创建的 appDataStore)

    // --- 用户特定 (User-Specific) 的 Key ---
    // (用于与 userId 绑定的 userDataStore)

    /**
     * 存储序列化后的用户信息的 ByteArray Key
     * 正如您提供的示例：val APP_USER_KEY = byteArrayPreferencesKey("app_user")
     */
    val APP_USER = byteArrayPreferencesKey("app_user")
}