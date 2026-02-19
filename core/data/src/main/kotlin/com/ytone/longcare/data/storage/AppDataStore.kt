package com.ytone.longcare.data.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// 定义一个常量作为 DataStore 的文件名
private const val APP_PREFERENCES_NAME = "app_prefs"

/**
 * 应用级别的 DataStore 实例，通过属性委托创建。
 * 它将用于存储非用户特定的、与应用本身相关的数据。
 */
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = APP_PREFERENCES_NAME)