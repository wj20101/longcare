package com.ytone.longcare.common.viewmodel

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import com.ytone.longcare.common.utils.ToastHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * ViewModel 的基类，提供一些通用的功能，例如显示 Toast。
 * @property toastHelper 用于显示 Toast 消息的帮助类。
 */
@HiltViewModel
class BaseViewModel @Inject constructor(
    private val toastHelper: ToastHelper
) : ViewModel() {

    /**
     * 显示一个短时间的 Toast 消息。
     * @param message 要显示的消息文本。
     */
    fun showShortToast(message: CharSequence) {
        toastHelper.showShort(message)
    }

    /**
     * 显示一个短时间的 Toast 消息。
     * @param resId 要显示的消息的字符串资源 ID。
     */
    fun showShortToast(@StringRes resId: Int) {
        toastHelper.showShort(resId)
    }

    /**
     * 显示一个长时间的 Toast 消息。
     * @param message 要显示的消息文本。
     */
    fun showLongToast(message: CharSequence) {
        toastHelper.showLong(message)
    }

    /**
     * 显示一个长时间的 Toast 消息。
     * @param resId 要显示的消息的字符串资源 ID。
     */
    fun showLongToast(@StringRes resId: Int) {
        toastHelper.showLong(resId)
    }

}