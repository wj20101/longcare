package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 运行真实版本分支；不把 Android 框架模拟当作厂商 ROM 验收。 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [24, 26, 28, 30, 31, 33, 34, 35, 36])
class ServiceCountdownPlatformVersionTest {
    @Test
    fun `only capabilities supported by this Android version need a special settings page`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val app = context.applicationContext as Application
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val location = shadowOf(context.getSystemService(LocationManager::class.java))
        if (Build.VERSION.SDK_INT >= 28) {
            location.setLocationEnabled(true)
        } else {
            @Suppress("DEPRECATION")
            location.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        }
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(true)
        if (Build.VERSION.SDK_INT >= 33) shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertEquals(null, requiredCountdownPermissionIssue(context, true, true))
        assertEquals(if (Build.VERSION.SDK_INT >= 34) ServiceCountdownPermissionIssue.FULL_SCREEN else null,
            requiredCountdownPermissionIssue(context, true, false))
        assertEquals(if (Build.VERSION.SDK_INT >= 34) Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            buildPermissionSettingsIntent(context, ServiceCountdownPermissionIssue.FULL_SCREEN).action)

        if (Build.VERSION.SDK_INT >= 31) {
            assertEquals(ServiceCountdownPermissionIssue.EXACT_ALARM,
                requiredCountdownPermissionIssue(context, false, true))
        }
        assertEquals(if (Build.VERSION.SDK_INT >= 31) Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            buildPermissionSettingsIntent(context, ServiceCountdownPermissionIssue.EXACT_ALARM).action)
        assertEquals(if (Build.VERSION.SDK_INT >= 26) Settings.ACTION_APP_NOTIFICATION_SETTINGS
            else Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            buildPermissionSettingsIntent(context, ServiceCountdownPermissionIssue.NOTIFICATION).action)

        if (Build.VERSION.SDK_INT >= 33) {
            shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
            assertEquals(ServiceCountdownPermissionIssue.NOTIFICATION,
                requiredCountdownPermissionIssue(context, true, true))
            shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        shadowOf(context.getSystemService(NotificationManager::class.java)).setNotificationsEnabled(false)
        assertEquals(ServiceCountdownPermissionIssue.NOTIFICATION,
            requiredCountdownPermissionIssue(context, true, true))
    }
}
