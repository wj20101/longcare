package com.ytone.longcare.features.servicecountdown.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.core.app.ActivityOptionsCompat
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ytone.longcare.model.OrderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Local ActivityResult responses exercise sequencing without opening Settings or changing orders. */
@SdkSuppress(minSdkVersion = 34)
class ServiceCountdownPermissionFlowTest {
    @get:Rule val compose = createComposeRule()

    @Before fun allowTestNotifications() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName, Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun initial_check_explains_missing_permission_without_requesting_it() {
        val fixture = Fixture()
        compose.setContent { Content(fixture, checkInitially = true) }
        compose.runOnIdle {
            assertEquals(listOf(ServiceCountdownPermissionIssue.NOTIFICATION), fixture.issues)
            assertEquals(0, fixture.runtimeRequests.size)
            assertEquals(null, fixture.settingsIntent)
        }
    }

    @Test fun notification_confirmation_requests_only_notifications_then_checks_special_permissions() {
        val fixture = Fixture()
        render(fixture)
        compose.runOnIdle { fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.NOTIFICATION) }
        compose.runOnIdle {
            assertEquals(listOf(listOf(Manifest.permission.POST_NOTIFICATIONS)), fixture.runtimeRequests)
            assertEquals(listOf(ServiceCountdownPermissionIssue.EXACT_ALARM), fixture.issues)
            fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.EXACT_ALARM)
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, fixture.settingsIntent?.action)
            fixture.exactAlarmAllowed = true
            fixture.returnFromSettings()
        }
        compose.runOnIdle {
            assertEquals(listOf(ServiceCountdownPermissionIssue.EXACT_ALARM,
                ServiceCountdownPermissionIssue.FULL_SCREEN), fixture.issues)
        }
    }

    @Test fun denied_notification_ends_the_prompt_flow_and_can_be_checked_manually() {
        val fixture = Fixture().apply { grantNotifications = false }
        render(fixture)
        compose.runOnIdle { fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.NOTIFICATION) }
        compose.runOnIdle {
            assertEquals(listOf<ServiceCountdownPermissionIssue?>(null), fixture.issues)
            assertEquals(1, fixture.runtimeRequests.size)
            assertEquals(null, fixture.settingsIntent)
            fixture.launchers.checkPermissions()
            assertEquals(ServiceCountdownPermissionIssue.NOTIFICATION, fixture.issues.last())
            assertEquals(1, fixture.runtimeRequests.size)
        }
    }

    @Test fun location_confirmation_requests_only_location_then_explains_notifications() {
        val fixture = Fixture().apply { locationAllowed = false }
        render(fixture)
        compose.runOnIdle { fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.LOCATION) }
        compose.runOnIdle {
            assertEquals(setOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                fixture.runtimeRequests.single().toSet())
            assertEquals(1, fixture.locationStarts)
            assertEquals(listOf(ServiceCountdownPermissionIssue.NOTIFICATION), fixture.issues)
        }
    }

    @Test fun denied_location_can_be_recovered_by_explicit_app_settings_action() {
        val fixture = Fixture().apply { locationAllowed = false; grantLocation = false }
        render(fixture)
        compose.runOnIdle { fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.LOCATION) }
        compose.runOnIdle {
            assertEquals(listOf<ServiceCountdownPermissionIssue?>(null), fixture.issues)
            assertEquals(0, fixture.locationStarts)
            fixture.launchers.openPermissionSettings(ServiceCountdownPermissionIssue.LOCATION)
            assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, fixture.settingsIntent?.action)
            fixture.locationAllowed = true
            fixture.notificationAllowed = true
            fixture.exactAlarmAllowed = true
            fixture.fullScreenAllowed = true
            fixture.issues.clear()
            fixture.returnFromSettings()
        }
        compose.runOnIdle {
            assertEquals(1, fixture.locationStarts)
            assertEquals(listOf<ServiceCountdownPermissionIssue?>(null), fixture.issues)
        }
    }

    @Test fun ungranted_exact_alarm_return_does_not_repeat_the_dialog() {
        verifySettingsReturn(ServiceCountdownPermissionIssue.EXACT_ALARM, granted = false)
    }

    @Test fun ungranted_full_screen_return_does_not_repeat_the_dialog() {
        verifySettingsReturn(ServiceCountdownPermissionIssue.FULL_SCREEN, granted = false)
    }

    @Test fun full_screen_grant_clears_the_permission_issue() {
        verifySettingsReturn(ServiceCountdownPermissionIssue.FULL_SCREEN, granted = true)
    }

    @Test fun restored_initial_check_does_not_prompt_again_or_launch_runtime_request() {
        val fixture = Fixture()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content(fixture, checkInitially = true) }
        compose.runOnIdle { assertEquals(1, fixture.issues.size) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle {
            assertEquals(1, fixture.issues.size)
            assertTrue(fixture.runtimeRequests.isEmpty())
        }
    }

    @Test fun pending_settings_request_survives_state_restoration_without_reprompting() {
        val fixture = Fixture().apply { notificationAllowed = true; exactAlarmAllowed = true }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content(fixture) }
        compose.runOnIdle { fixture.launchers.requestPermission(ServiceCountdownPermissionIssue.FULL_SCREEN) }
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { fixture.returnFromSettings() }
        compose.runOnIdle {
            assertEquals(listOf<ServiceCountdownPermissionIssue?>(null), fixture.issues)
            assertTrue(fixture.runtimeRequests.isEmpty())
        }
    }

    private fun verifySettingsReturn(issue: ServiceCountdownPermissionIssue, granted: Boolean) {
        val fixture = Fixture().apply {
            notificationAllowed = true
            exactAlarmAllowed = issue != ServiceCountdownPermissionIssue.EXACT_ALARM
        }
        render(fixture)
        compose.runOnIdle {
            fixture.launchers.requestPermission(issue)
            assertEquals(if (issue == ServiceCountdownPermissionIssue.FULL_SCREEN)
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT else Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                fixture.settingsIntent?.action)
            assertEquals("package:${fixture.context.packageName}", fixture.settingsIntent?.dataString)
            if (granted) fixture.fullScreenAllowed = true
            fixture.returnFromSettings()
        }
        compose.runOnIdle {
            assertEquals(listOf<ServiceCountdownPermissionIssue?>(null), fixture.issues)
            assertTrue(fixture.runtimeRequests.isEmpty())
        }
    }

    private fun render(fixture: Fixture) = compose.setContent { Content(fixture) }

    @Composable
    private fun Content(fixture: Fixture, checkInitially: Boolean = false) {
        val appContext = LocalContext.current
        fixture.context = object : ContextWrapper(appContext) {
            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                when (permission) {
                    Manifest.permission.POST_NOTIFICATIONS -> if (fixture.notificationAllowed)
                        PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION ->
                        if (fixture.locationAllowed) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
                    else -> super.checkPermission(permission, pid, uid)
                }
        }
        val owner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = fixture.registry
        }
        CompositionLocalProvider(LocalContext provides fixture.context, LocalActivityResultRegistryOwner provides owner) {
            fixture.launchers = rememberServiceCountdownScreenLaunchers(
                orderKey = OrderKey(42, 7),
                canScheduleExactAlarms = { fixture.exactAlarmAllowed },
                canUseFullScreenIntent = { fixture.fullScreenAllowed },
                onLocationPermissionGranted = { fixture.locationStarts++ },
                onPermissionDialogRequired = { fixture.issues += it },
            )
            if (checkInitially) {
                ServiceCountdownInitialPermissionCheck(OrderKey(42, 7), fixture.launchers.checkPermissions)
            }
        }
    }

    private class Fixture {
        var notificationAllowed = false
        var grantNotifications = true
        var locationAllowed = true
        var grantLocation = true
        var locationStarts = 0
        val runtimeRequests = mutableListOf<List<String>>()
        var exactAlarmAllowed = false
        var fullScreenAllowed = false
        var settingsRequestCode = -1
        var settingsIntent: Intent? = null
        val issues = mutableListOf<ServiceCountdownPermissionIssue?>()
        lateinit var context: Context
        lateinit var launchers: ServiceCountdownScreenLaunchers
        val registry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
                input: I, options: ActivityOptionsCompat?) {
                when (contract) {
                    is ActivityResultContracts.RequestMultiplePermissions -> {
                        @Suppress("UNCHECKED_CAST") val permissions = input as Array<String>
                        runtimeRequests += permissions.toList()
                        if (Manifest.permission.POST_NOTIFICATIONS in permissions) notificationAllowed = grantNotifications
                        if (Manifest.permission.ACCESS_FINE_LOCATION in permissions) locationAllowed = grantLocation
                        dispatchResult(requestCode, permissions.associateWith {
                            if (it == Manifest.permission.POST_NOTIFICATIONS) grantNotifications else grantLocation
                        })
                    }
                    is ActivityResultContracts.StartActivityForResult -> {
                        settingsRequestCode = requestCode
                        settingsIntent = input as Intent
                    }
                    else -> error("Unexpected activity result contract")
                }
            }
        }

        fun returnFromSettings() {
            registry.dispatchResult(settingsRequestCode, ActivityResult(Activity.RESULT_OK, null))
        }
    }
}
