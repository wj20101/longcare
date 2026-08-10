package com.ytone.longcare.platform.text

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Keeps Android resource access outside SalesViewModel. */
@Singleton
class SalesTextResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun text(
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String =
        if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
}
