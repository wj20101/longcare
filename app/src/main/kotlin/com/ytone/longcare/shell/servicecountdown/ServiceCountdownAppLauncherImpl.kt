package com.ytone.longcare.shell.servicecountdown

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import com.ytone.longcare.MainActivity
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownAppLauncher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServiceCountdownAppLauncherImpl @Inject constructor() : ServiceCountdownAppLauncher {
    override fun createCountdownContentIntent(
        context: Context,
        orderId: Long,
        requestCode: Int,
        dataUri: android.net.Uri,
    ): PendingIntent? {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = dataUri
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("orderId", orderId)
        }
        return PendingIntentCompat.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT,
            false,
        )
    }
}
