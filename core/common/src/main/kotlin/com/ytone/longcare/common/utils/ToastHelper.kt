package com.ytone.longcare.common.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToastHelper @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun showShort(message: CharSequence) {
        showToastOnMainThread(message, Toast.LENGTH_SHORT)
    }

    fun showShort(@StringRes resId: Int) {
        showToastOnMainThread(context.getString(resId), Toast.LENGTH_SHORT)
    }

    fun showLong(message: CharSequence) {
        showToastOnMainThread(message, Toast.LENGTH_LONG)
    }

    fun showLong(@StringRes resId: Int) {
        showToastOnMainThread(context.getString(resId), Toast.LENGTH_LONG)
    }

    private fun showToastOnMainThread(message: CharSequence, duration: Int) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Toast.makeText(context, message, duration).show()
            return
        }
        mainHandler.post { Toast.makeText(context, message, duration).show() }
    }
}
