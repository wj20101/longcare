package com.ytone.longcare.common.utils

import android.app.ActivityManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.core.common.R
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
class DeviceCompatibilityHelperTest {
    private lateinit var context: Context
    private lateinit var packageManager: PackageManager
    private lateinit var activityManager: ActivityManager
    private lateinit var powerManager: PowerManager
    private val startedIntents = mutableListOf<Intent>()

    @Before
    fun setUp() {
        val application = ApplicationProvider.getApplicationContext<Context>()
        application.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context = mockk(relaxed = true)
        packageManager = mockk(relaxed = true)
        activityManager = mockk()
        powerManager = mockk()
        every { context.packageName } returns application.packageName
        every { context.applicationInfo } returns application.applicationInfo
        every { context.packageManager } returns packageManager
        every { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) } returns
            application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        every { context.getSystemService(ActivityManager::class.java) } returns activityManager
        every { context.getSystemService(PowerManager::class.java) } returns powerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            every { activityManager.isBackgroundRestricted } returns false
        }
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns false
        every { context.checkSelfPermission(any()) } returns PackageManager.PERMISSION_DENIED
        every { packageManager.resolveActivity(any<Intent>(), any<Int>()) } returns exportedActivity()
        every { context.startActivity(capture(startedIntents)) } answers { Unit }
        ShadowBuild.setManufacturer("Google")
    }

    @Test
    fun `supported manufacturers expose relevant manual checks without private permission probes`() {
        val expectedBatteryMessages = mapOf(
            "Xiaomi" to R.string.compatibility_battery_xiaomi,
            "Redmi" to R.string.compatibility_battery_xiaomi,
            "Huawei" to R.string.compatibility_battery_huawei,
            "Honor" to R.string.compatibility_battery_huawei,
            "OPPO" to R.string.compatibility_battery_oppo,
            "realme" to R.string.compatibility_battery_oppo,
            "vivo" to R.string.compatibility_battery_vivo,
        )
        expectedBatteryMessages.forEach { (manufacturer, message) ->
            ShadowBuild.setManufacturer(manufacturer)
            val guides = DeviceCompatibilityHelper.getAllGuides(context)
            assertEquals(manufacturer, PermissionGuideId.entries.toSet(), guides.map { it.id }.toSet())
            assertEquals(manufacturer, message, guides.first().messageRes)
            assertEquals(manufacturer, R.string.compatibility_status_manual_check, guides[1].statusRes)
            assertEquals(manufacturer, R.string.compatibility_status_manual_check, guides[2].statusRes)
            if (manufacturer !in setOf("Xiaomi", "Redmi")) {
                guides.forEach { guide -> assertAppDetails(guide.settingsIntent) }
            }
        }
    }

    @Test
    fun `Xiaomi restricts autostart intent to security center and retains verified settings targets`() {
        ShadowBuild.setManufacturer("Xiaomi")
        val guides = DeviceCompatibilityHelper.getAllGuides(context).associateBy { it.id }
        assertEquals("com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
            guides.getValue(PermissionGuideId.BATTERY_OPTIMIZATION).settingsIntent.component?.className)
        val autoStart = guides.getValue(PermissionGuideId.AUTO_START).settingsIntent
        assertEquals("miui.intent.action.OP_AUTO_START", autoStart.action)
        assertEquals("com.miui.securitycenter", autoStart.`package`)
        val popup = guides.getValue(PermissionGuideId.MANUFACTURER_POPUP).settingsIntent
        assertEquals("miui.intent.action.APP_PERM_EDITOR", popup.action)
        assertEquals("com.miui.securitycenter", popup.`package`)
        assertEquals(context.packageName, popup.getStringExtra("extra_pkgname"))
        assertEquals(setOf(Intent.CATEGORY_DEFAULT), popup.categories)
        assertEquals(null, popup.component)
    }

    @Test
    fun `Samsung uses its documented never sleeping apps action without inventing autostart permission`() {
        ShadowBuild.setManufacturer("samsung")
        val guides = DeviceCompatibilityHelper.getAllGuides(context)
        assertEquals(1, guides.size)
        val battery = guides.single()
        assertEquals(R.string.compatibility_battery_samsung, battery.messageRes)
        assertEquals("com.samsung.android.lool", battery.settingsIntent.`package`)
        assertEquals("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY", battery.settingsIntent.action)
        assertEquals(2, battery.settingsIntent.getIntExtra("activity_type", -1))
    }

    @Test
    fun `generic device offers standard battery settings and manual access remains after exemption`() {
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, battery.settingsIntent.action)
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        assertTrue(DeviceCompatibilityHelper.getAllRequiredGuides(context).isEmpty())
        assertEquals(1, DeviceCompatibilityHelper.getAllGuides(context).size)
    }

    @Test
    fun `generic battery exemption does not hide known background restriction`() {
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        every { activityManager.isBackgroundRestricted } returns true

        val battery = DeviceCompatibilityHelper.getAllRequiredGuides(context).single()

        assertEquals(PermissionGuideId.BATTERY_OPTIMIZATION, battery.id)
        assertEquals(R.string.compatibility_status_restricted_exempt, battery.statusRes)
    }

    @Test
    fun `generic battery exemption does not hide unknown background restriction`() {
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        every { activityManager.isBackgroundRestricted } throws SecurityException("unavailable")

        val battery = DeviceCompatibilityHelper.getAllRequiredGuides(context).single()

        assertEquals(PermissionGuideId.BATTERY_OPTIMIZATION, battery.id)
        assertEquals(R.string.compatibility_status_exempt, battery.statusRes)
    }

    @Test
    fun `shown suppresses repeated automatic guidance even while background remains restricted`() {
        ShadowBuild.setManufacturer("vivo")
        every { activityManager.isBackgroundRestricted } returns true
        val all = DeviceCompatibilityHelper.getAllGuides(context)
        all.forEach { DeviceCompatibilityHelper.markGuideShown(context, it.id) }
        assertTrue(DeviceCompatibilityHelper.getAllRequiredGuides(context).isEmpty())
        val manual = DeviceCompatibilityHelper.getAllGuides(context)
        assertEquals(all.map { it.id }, manual.map { it.id })
        assertEquals(R.string.compatibility_status_restricted_not_exempt, manual.first().statusRes)
    }

    @Test
    fun `old shown keys remain compatible but do not imply authorization`() {
        ShadowBuild.setManufacturer("Xiaomi")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("auto_start_guide_shown", true)
            .putBoolean("manufacturer_guide_shown", true)
            .commit()
        assertEquals(listOf(PermissionGuideId.BATTERY_OPTIMIZATION),
            DeviceCompatibilityHelper.getAllRequiredGuides(context).map { it.id })
        assertEquals(3, DeviceCompatibilityHelper.getAllGuides(context).size)
    }

    @Test
    fun `opening a setting records neither shown nor permission granted`() {
        ShadowBuild.setManufacturer("Xiaomi")
        val before = DeviceCompatibilityHelper.getAllRequiredGuides(context)
        assertTrue(DeviceCompatibilityHelper.openGuide(context, before.last()))
        assertEquals(before.map { it.id }, DeviceCompatibilityHelper.getAllRequiredGuides(context).map { it.id })
        assertTrue(startedIntents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `Activity opens settings in its natural back stack without new task flag`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val guide = DeviceCompatibilityHelper.getAllGuides(context).single().copy(
            settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:${activity.packageName}")),
        )
        assertTrue(DeviceCompatibilityHelper.openGuide(activity, guide))
        val launched = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, launched.action)
        assertEquals(0, launched.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertEquals(0, guide.settingsIntent.flags)
    }

    @Test
    fun `non Activity context retains required new task flag without modifying guide intent`() {
        val guide = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertTrue(DeviceCompatibilityHelper.openGuide(context, guide))
        assertTrue(startedIntents.single().flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertEquals(0, guide.settingsIntent.flags)
    }

    @Test
    fun `system restriction and battery exemption are independent of manufacturer configuration`() {
        ShadowBuild.setManufacturer("Samsung")
        DeviceCompatibilityHelper.markGuideShown(context, PermissionGuideId.BATTERY_OPTIMIZATION)
        every { activityManager.isBackgroundRestricted } returns true
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        assertTrue(DeviceCompatibilityHelper.getAllRequiredGuides(context).isEmpty())
        val restricted = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(R.string.compatibility_status_restricted_exempt, restricted.statusRes)
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns false
        assertEquals(R.string.compatibility_status_restricted_not_exempt,
            DeviceCompatibilityHelper.getAllGuides(context).single().statusRes)
        assertTrue(DeviceCompatibilityHelper.getAllRequiredGuides(context).isEmpty())
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        every { activityManager.isBackgroundRestricted } returns false
        assertTrue(DeviceCompatibilityHelper.getAllRequiredGuides(context).isEmpty())
        assertEquals(R.string.compatibility_status_unrestricted_exempt,
            DeviceCompatibilityHelper.getAllGuides(context).single().statusRes)
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns false
        assertEquals(R.string.compatibility_status_unrestricted_not_exempt,
            DeviceCompatibilityHelper.getAllGuides(context).single().statusRes)
        ShadowBuild.setManufacturer("vivo")
        assertEquals(3, DeviceCompatibilityHelper.getAllGuides(context).size)
    }

    @Test
    fun `unreadable system state is unknown and never treated as granted`() {
        every { activityManager.isBackgroundRestricted } throws SecurityException("unavailable")
        every { powerManager.isIgnoringBatteryOptimizations(any()) } throws SecurityException("unavailable")
        val battery = DeviceCompatibilityHelper.getAllRequiredGuides(context).single()
        assertEquals(R.string.compatibility_status_unknown, battery.statusRes)
    }

    @Test
    @Config(sdk = [27])
    fun `Android before 28 does not query unsupported background restriction API`() {
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(R.string.compatibility_status_not_exempt, battery.statusRes)
        every { powerManager.isIgnoringBatteryOptimizations(any()) } returns true
        assertEquals(R.string.compatibility_status_exempt,
            DeviceCompatibilityHelper.getAllRequiredGuides(context).single().statusRes)
        verify(exactly = 0) { context.getSystemService(ActivityManager::class.java) }
    }

    @Test
    fun `unresolved manufacturer settings fall back to app details even when packages are invisible`() {
        ShadowBuild.setManufacturer("Samsung")
        every { packageManager.resolveActivity(any<Intent>(), any<Int>()) } returns null
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, battery.settingsIntent.action)
        assertTrue(DeviceCompatibilityHelper.openGuide(context, battery))
        assertAppDetails(startedIntents.single())
    }

    @Test
    fun `unexported and permission protected activities are not launched`() {
        ShadowBuild.setManufacturer("Samsung")
        every { packageManager.resolveActivity(any<Intent>(), any<Int>()) } returns exportedActivity(exported = false)
        val unexported = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, unexported.settingsIntent.action)
        every { packageManager.resolveActivity(any<Intent>(), any<Int>()) } returns exportedActivity(permission = "vendor.signature.PERMISSION")
        val protected = DeviceCompatibilityHelper.getAllGuides(context).single()
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, protected.settingsIntent.action)
    }

    @Test
    fun `activity disabled after collecting guide falls back without losing manual entry`() {
        ShadowBuild.setManufacturer("Samsung")
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        every { packageManager.resolveActivity(any<Intent>(), any<Int>()) } returns exportedActivity(enabled = false)
        assertTrue(DeviceCompatibilityHelper.openGuide(context, battery))
        assertAppDetails(startedIntents.single())
    }

    @Test
    fun `manufacturer activity launch failure falls back to app details`() {
        ShadowBuild.setManufacturer("Samsung")
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        every { context.startActivity(any()) } answers {
            val intent = firstArg<Intent>()
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) throw SecurityException("vendor denied")
            startedIntents += intent
        }
        assertTrue(DeviceCompatibilityHelper.openGuide(context, battery))
        assertAppDetails(startedIntents.single())
    }

    @Test
    fun `all setting launches failing return false without marking guide shown`() {
        ShadowBuild.setManufacturer("Samsung")
        val battery = DeviceCompatibilityHelper.getAllGuides(context).single()
        every { context.startActivity(any()) } throws ActivityNotFoundException()
        assertFalse(DeviceCompatibilityHelper.openGuide(context, battery))
        assertEquals(1, DeviceCompatibilityHelper.getAllRequiredGuides(context).size)
    }

    private fun assertAppDetails(intent: Intent) {
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals("package:${context.packageName}", intent.data.toString())
    }

    private fun exportedActivity(
        exported: Boolean = true,
        enabled: Boolean = true,
        permission: String? = null,
    ) = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            this.exported = exported
            this.enabled = enabled
            this.permission = permission
            applicationInfo = ApplicationInfo().apply { this.enabled = true }
        }
    }

    private companion object {
        const val PREFS = "device_compatibility_prefs"
    }
}
