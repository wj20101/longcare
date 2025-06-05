package com.ytone.longcare.common.utils

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToastHelper @Inject constructor(@ApplicationContext private val context: Context) {

    fun showShort(message: CharSequence) {
        context.showShortToast(message)
    }

    fun showShort(@StringRes resId: Int) {
        context.showShortToast(resId)
    }

    fun showLong(message: CharSequence) {
        context.showLongToast(message)
    }

    fun showLong(@StringRes resId: Int) {
        context.showLongToast(resId)
    }
}