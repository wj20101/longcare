package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 隐私政策同意状态管理。
 * 用于在用户同意隐私政策前阻止 SDK 初始化和设备标识符采集。
 */
@Singleton
class PrivacyConsentManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    @Volatile
    private var cachedConsent: Boolean? = null

    /**
     * 用户是否已同意隐私政策。
     */
    val isPrivacyConsented: Boolean
        get() {
            cachedConsent?.let { return it }
            return prefs.getBoolean(KEY_PRIVACY_CONSENTED, false).also {
                cachedConsent = it
            }
        }

    /**
     * 记录用户同意隐私政策。
     * 先更新内存缓存再同步写入磁盘，避免竞态条件。
     */
    fun markConsented() {
        cachedConsent = true
        prefs.edit().putBoolean(KEY_PRIVACY_CONSENTED, true).commit()
    }

    /**
     * 重置同意状态（用于测试或注销场景）。
     */
    fun resetConsent() {
        cachedConsent = null
        prefs.edit().remove(KEY_PRIVACY_CONSENTED).commit()
    }

    private companion object {
        const val PREFS_NAME = "privacy_consent"
        const val KEY_PRIVACY_CONSENTED = "is_consented"
    }
}
