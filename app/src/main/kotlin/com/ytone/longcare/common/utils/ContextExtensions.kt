package com.ytone.longcare.common.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes

/**
 * 显示一个短时间的 Toast 消息。
 * 确保在主线程中执行，避免 "Can't toast on a thread that has not called Looper.prepare()" 崩溃。
 *
 * @param message 要显示的消息文本。
 */
fun Context.showShortToast(message: CharSequence) {
    showToastOnMainThread(message, Toast.LENGTH_SHORT)
}

/**
 * 显示一个短时间的 Toast 消息。
 * 确保在主线程中执行，避免 "Can't toast on a thread that has not called Looper.prepare()" 崩溃。
 *
 * @param resId 要显示的消息的字符串资源 ID。
 */
fun Context.showShortToast(@StringRes resId: Int) {
    showToastOnMainThread(getString(resId), Toast.LENGTH_SHORT)
}

/**
 * 显示一个长时间的 Toast 消息。
 * 确保在主线程中执行，避免 "Can't toast on a thread that has not called Looper.prepare()" 崩溃。
 *
 * @param message 要显示的消息文本。
 */
fun Context.showLongToast(message: CharSequence) {
    showToastOnMainThread(message, Toast.LENGTH_LONG)
}

/**
 * 显示一个长时间的 Toast 消息。
 * 确保在主线程中执行，避免 "Can't toast on a thread that has not called Looper.prepare()" 崩溃。
 *
 * @param resId 要显示的消息的字符串资源 ID。
 */
fun Context.showLongToast(@StringRes resId: Int) {
    showToastOnMainThread(getString(resId), Toast.LENGTH_LONG)
}

/**
 * 在主线程中显示 Toast，如果当前已经在主线程则直接显示，否则通过 Handler 切换到主线程。
 *
 * @param message 要显示的消息文本。
 * @param duration Toast 显示的时长。
 */
private fun Context.showToastOnMainThread(message: CharSequence, duration: Int) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        // 当前已在主线程，直接显示
        Toast.makeText(this, message, duration).show()
    } else {
        // 当前在后台线程，切换到主线程
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(this, message, duration).show()
        }
    }
}
