package com.ytone.longcare.features.servicecountdown.domain

import android.app.PendingIntent
import android.content.Context
import android.net.Uri

/**
 * 封装倒计时通知点击后的 App 跳转能力，避免 feature 直接依赖 app 入口实现。
 */
interface ServiceCountdownAppLauncher {
    fun createCountdownContentIntent(
        context: Context,
        orderId: Long,
        requestCode: Int,
        dataUri: Uri,
    ): PendingIntent?
}
