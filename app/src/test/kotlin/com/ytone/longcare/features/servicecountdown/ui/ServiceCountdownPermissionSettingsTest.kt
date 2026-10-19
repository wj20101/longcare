package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.location.LocationManager
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.features.countdown.manager.CountdownNotificationManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class ServiceCountdownPermissionSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `available permission setting uses its existing intent without fallback`() {
        val launched = mutableListOf<Intent>()
        var failures = 0
        openCountdownPermissionSettings(context, ServiceCountdownPermissionIssue.EXACT_ALARM,
            launch = { launched += it }, onUnavailable = { failures++ })
        assertEquals(listOf(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM), launched.map { it.action })
        assertEquals("package:${context.packageName}", launched.single().dataString)
        assertEquals(0, failures)
    }

    @Test
    fun `missing standard permission page falls back to the same apps details`() {
        verifyFallback(ActivityNotFoundException())
    }

    @Test
    fun `ROM blocking standard permission page falls back to the same apps details`() {
        verifyFallback(SecurityException("ROM denied standard settings"))
    }

    @Test
    fun `both settings unavailable reports failure once after bounded attempts`() {
        val attempted = mutableListOf<Intent>()
        var failures = 0
        openCountdownPermissionSettings(context, ServiceCountdownPermissionIssue.FULL_SCREEN,
            launch = {
                attempted += it
                throw ActivityNotFoundException()
            }, onUnavailable = { failures++ })
        assertEquals(listOf(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS), attempted.map { it.action })
        assertEquals(1, failures)
    }

    @Test
    fun `app details failure is not retried as an identical fallback`() {
        var attempts = 0
        var failures = 0
        openCountdownPermissionSettings(context, ServiceCountdownPermissionIssue.LOCATION,
            launch = { attempts++; throw SecurityException("settings unavailable") },
            onUnavailable = { failures++ })
        assertEquals(1, attempts)
        assertEquals(1, failures)
    }

    @Test
    @Config(sdk = [28])
    fun `low countdown channel importance is separate from runtime notification permission`() {
        allowLocation()
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID, "Service reminder", NotificationManager.IMPORTANCE_DEFAULT))

        assertEquals(ServiceCountdownPermissionIssue.CHANNEL,
            requiredCountdownPermissionIssue(context, canScheduleExactAlarms = true, canUseFullScreenIntent = true))
        val intent = buildPermissionSettingsIntent(context, ServiceCountdownPermissionIssue.CHANNEL)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID,
            intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    @Config(sdk = [28])
    fun `blocked notification group is detected even when its channel importance is high`() {
        allowLocation()
        val notifications = context.getSystemService(NotificationManager::class.java)
        val group = NotificationChannelGroup("service", "Service")
        // Emulate the user's group switch, which applications cannot change through the public API.
        ReflectionHelpers.setField(group, "mBlocked", true)
        notifications.createNotificationChannelGroup(group)
        notifications.createNotificationChannel(NotificationChannel(
            CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID, "Service reminder", NotificationManager.IMPORTANCE_HIGH
        ).apply { this.group = group.id })

        assertEquals(ServiceCountdownPermissionIssue.CHANNEL,
            requiredCountdownPermissionIssue(context, canScheduleExactAlarms = true, canUseFullScreenIntent = true))
        // The application notification page exposes the group switch; the channel page alone does not.
        val intent = buildPermissionSettingsIntent(context, ServiceCountdownPermissionIssue.CHANNEL)
        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    @Config(sdk = [28])
    fun `high importance in an enabled group has no permission issue`() {
        allowLocation()
        val notifications = context.getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannelGroup(NotificationChannelGroup("service", "Service"))
        notifications.createNotificationChannel(NotificationChannel(
            CountdownNotificationManager.COUNTDOWN_NOTIFICATION_CHANNEL_ID, "Service reminder", NotificationManager.IMPORTANCE_HIGH
        ).apply { group = "service" })

        assertEquals(null,
            requiredCountdownPermissionIssue(context, canScheduleExactAlarms = true, canUseFullScreenIntent = true))
    }

    private fun allowLocation() {
        shadowOf(context.applicationContext as Application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
    }

    private fun verifyFallback(exception: RuntimeException) {
        val attempted = mutableListOf<Intent>()
        var failures = 0
        openCountdownPermissionSettings(context, ServiceCountdownPermissionIssue.FULL_SCREEN,
            launch = {
                attempted += it
                if (it.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) throw exception
            }, onUnavailable = { failures++ })
        assertEquals(listOf(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS), attempted.map { it.action })
        assertEquals("package:${context.packageName}", attempted.last().dataString)
        assertEquals(0, failures)
    }
}
