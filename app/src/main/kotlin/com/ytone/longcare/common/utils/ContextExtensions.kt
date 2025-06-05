package com.ytone.longcare.common.utils

import android.content.Context
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 显示一个短时间的 Toast 消息。
 *
 * @param message 要显示的消息文本。
 */
fun Context.showShortToast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

/**
 * 显示一个短时间的 Toast 消息。
 *
 * @param resId 要显示的消息的字符串资源 ID。
 */
fun Context.showShortToast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
}

/**
 * 显示一个长时间的 Toast 消息。
 *
 * @param message 要显示的消息文本。
 */
fun Context.showLongToast(message: CharSequence) {
    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}

/**
 * 显示一个长时间的 Toast 消息。
 *
 * @param resId 要显示的消息的字符串资源 ID。
 */
fun Context.showLongToast(@StringRes resId: Int) {
    Toast.makeText(this, resId, Toast.LENGTH_LONG).show()
}
