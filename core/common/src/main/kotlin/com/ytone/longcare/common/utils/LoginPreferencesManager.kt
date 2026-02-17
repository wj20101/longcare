package com.ytone.longcare.common.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 登录相关的SharedPreferences管理类
 * 用于存储和读取上次登录成功的手机号码
 */
@Singleton
class LoginPreferencesManager @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    /**
     * 保存上次登录成功的手机号码
     */
    fun saveLastLoginPhoneNumber(phoneNumber: String) {
        sharedPreferences.edit { putString(KEY_LAST_LOGIN_PHONE, phoneNumber) }
    }

    /**
     * 获取上次登录成功的手机号码
     * @return 上次登录的手机号码，如果没有则返回空字符串
     */
    fun getLastLoginPhoneNumber(): String {
        return sharedPreferences.getString(KEY_LAST_LOGIN_PHONE, "") ?: ""
    }

    /**
     * 清除保存的手机号码
     */
    fun clearLastLoginPhoneNumber() {
        sharedPreferences.edit { remove(KEY_LAST_LOGIN_PHONE) }
    }

    companion object {
        private const val PREFS_NAME = "login_preferences"
        private const val KEY_LAST_LOGIN_PHONE = "last_login_phone_number"
    }
}
