package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
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
     */
    fun markConsented() {
        prefs.edit { putBoolean(KEY_PRIVACY_CONSENTED, true) }
        cachedConsent = true
    }

    /**
     * 重置同意状态（用于测试或注销场景）。
     */
    fun resetConsent() {
        prefs.edit { remove(KEY_PRIVACY_CONSENTED) }
        cachedConsent = null
    }

    private companion object {
        const val PREFS_NAME = "privacy_consent"
        const val KEY_PRIVACY_CONSENTED = "is_consented"
    }
}
