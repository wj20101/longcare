package com.ytone.longcare.common.utils

import android.app.ActivityManager
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import com.ytone.longcare.core.common.R
import java.util.Locale

/** 厂商设置只提供人工核对入口，不把私有接口或“已展示”当成授权状态。 */
object DeviceCompatibilityHelper {
    private const val PREFS_NAME = "device_compatibility_prefs"
    private const val KEY_BATTERY_GUIDE_SHOWN = "battery_guide_shown"
    // 沿用旧 key，升级后不会再次逐项打扰已看过引导的用户。
    private const val KEY_AUTO_START_GUIDE_SHOWN = "auto_start_guide_shown"
    private const val KEY_MANUFACTURER_GUIDE_SHOWN = "manufacturer_guide_shown"

    private enum class Manufacturer { XIAOMI, HUAWEI, OPPO, VIVO, SAMSUNG, OTHER }

    private fun manufacturer(): Manufacturer = when (Build.MANUFACTURER.lowercase(Locale.ROOT)) {
        "xiaomi", "redmi" -> Manufacturer.XIAOMI
        "huawei", "honor" -> Manufacturer.HUAWEI
        "oppo", "realme" -> Manufacturer.OPPO
        "vivo" -> Manufacturer.VIVO
        "samsung" -> Manufacturer.SAMSUNG
        else -> Manufacturer.OTHER
    }

    /** 自动推荐只提示一次；用户稍后仍可通过手动入口核对当前状态。 */
    fun getAllRequiredGuides(context: Context): List<PermissionGuideItem> {
        val batteryExempt = isIgnoringBatteryOptimizations(context)
        val backgroundRestricted = isBackgroundRestricted(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return buildGuides(context, backgroundRestricted, batteryExempt).filter { item ->
            !prefs.getBoolean(shownKey(item.id), false) &&
                // 标准系统电池豁免不能证明厂商的“无限制/从不休眠”已配置。
                // 通用系统也只有明确未受限时才能省略；未知状态仍供人工核对。
                !(item.id == PermissionGuideId.BATTERY_OPTIMIZATION &&
                    manufacturer() == Manufacturer.OTHER && batteryExempt == true && backgroundRestricted == false)
        }
    }

    /** 手动检查入口始终可用，不受“已展示”记录影响。 */
    fun getAllGuides(context: Context): List<PermissionGuideItem> = buildGuides(
        context,
        isBackgroundRestricted(context),
        isIgnoringBatteryOptimizations(context),
    )

    private fun buildGuides(
        context: Context,
        backgroundRestricted: Boolean?,
        batteryExempt: Boolean?,
    ): List<PermissionGuideItem> {
        val manufacturer = manufacturer()
        val items = mutableListOf(
            PermissionGuideItem(
                id = PermissionGuideId.BATTERY_OPTIMIZATION,
                titleRes = R.string.compatibility_battery_title_power_strategy,
                messageRes = batteryMessage(manufacturer),
                settingsIntent = availableOrAppSettings(context, batteryIntent(context, manufacturer)),
                statusRes = batteryStatus(backgroundRestricted, batteryExempt),
            )
        )
        if (manufacturer in listOf(Manufacturer.XIAOMI, Manufacturer.HUAWEI, Manufacturer.OPPO, Manufacturer.VIVO)) {
            items += PermissionGuideItem(
                id = PermissionGuideId.AUTO_START,
                titleRes = R.string.compatibility_auto_start_title,
                messageRes = autoStartMessage(manufacturer),
                settingsIntent = availableOrAppSettings(context, autoStartIntent(manufacturer)),
                statusRes = R.string.compatibility_status_manual_check,
            )
            items += PermissionGuideItem(
                id = PermissionGuideId.MANUFACTURER_POPUP,
                titleRes = R.string.compatibility_popup_permission_title,
                messageRes = popupMessage(manufacturer),
                settingsIntent = availableOrAppSettings(context, popupIntent(context, manufacturer)),
                statusRes = R.string.compatibility_status_manual_check,
            )
        }
        return items
    }

    /** 打开设置不代表用户改过设置，也不修改提示记录。 */
    fun openGuide(context: Context, item: PermissionGuideItem): Boolean {
        val appSettings = appSettingsIntent(context)
        val intent = item.settingsIntent
        if (intent.filterEquals(appSettings) || isActivityAvailable(context, intent)) {
            if (startActivity(context, intent)) return true
        }
        return !intent.filterEquals(appSettings) && startActivity(context, appSettings)
    }

    /** 仅记录已展示，用于减少自动提示；不记录任何授权结论。 */
    fun markGuideShown(context: Context, guideId: PermissionGuideId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(shownKey(guideId), true) }
    }

    private fun shownKey(id: PermissionGuideId): String = when (id) {
        PermissionGuideId.BATTERY_OPTIMIZATION -> KEY_BATTERY_GUIDE_SHOWN
        PermissionGuideId.AUTO_START -> KEY_AUTO_START_GUIDE_SHOWN
        PermissionGuideId.MANUFACTURER_POPUP -> KEY_MANUFACTURER_GUIDE_SHOWN
    }

    private fun isBackgroundRestricted(context: Context): Boolean? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching { context.getSystemService<ActivityManager>()?.isBackgroundRestricted }.getOrNull()
    }

    private fun isIgnoringBatteryOptimizations(context: Context): Boolean? =
        runCatching {
            context.getSystemService<PowerManager>()?.isIgnoringBatteryOptimizations(context.packageName)
        }.getOrNull()

    @StringRes
    private fun batteryStatus(restricted: Boolean?, exempt: Boolean?): Int = when {
        restricted == true && exempt == true -> R.string.compatibility_status_restricted_exempt
        restricted == true && exempt == false -> R.string.compatibility_status_restricted_not_exempt
        restricted == true -> R.string.compatibility_status_restricted
        restricted == false && exempt == true -> R.string.compatibility_status_unrestricted_exempt
        restricted == false && exempt == false -> R.string.compatibility_status_unrestricted_not_exempt
        exempt == true -> R.string.compatibility_status_exempt
        exempt == false -> R.string.compatibility_status_not_exempt
        else -> R.string.compatibility_status_unknown
    }

    private fun batteryIntent(context: Context, manufacturer: Manufacturer): Intent? = when (manufacturer) {
        Manufacturer.XIAOMI -> Intent().setClassName(
            "com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            .putExtra("package_name", context.packageName)
            .putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
        // Samsung 官方公布的“从不休眠的应用”入口；旧 One UI 不支持时回退应用详情。
        Manufacturer.SAMSUNG -> Intent("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY")
            .setPackage("com.samsung.android.lool").putExtra("activity_type", 2)
        Manufacturer.OTHER -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        else -> null
    }

    private fun autoStartIntent(manufacturer: Manufacturer): Intent? =
        if (manufacturer == Manufacturer.XIAOMI) {
            Intent("miui.intent.action.OP_AUTO_START")
                .setPackage("com.miui.securitycenter").addCategory(Intent.CATEGORY_DEFAULT)
        } else null

    private fun popupIntent(context: Context, manufacturer: Manufacturer): Intent? =
        if (manufacturer == Manufacturer.XIAOMI) {
            // 使用小米官方应用权限管理 action，避免依赖不同 ROM 的私有 Activity 类名。
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setPackage("com.miui.securitycenter")
                .addCategory(Intent.CATEGORY_DEFAULT)
                .putExtra("extra_pkgname", context.packageName)
        } else null

    private fun appSettingsIntent(context: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        "package:${context.packageName}".toUri(),
    )

    private fun availableOrAppSettings(context: Context, intent: Intent?): Intent =
        intent?.takeIf { isActivityAvailable(context, it) } ?: appSettingsIntent(context)

    private fun isActivityAvailable(context: Context, intent: Intent): Boolean = runCatching {
        val activity = context.packageManager.resolveActivity(intent, 0)?.activityInfo ?: return false
        activity.exported && activity.enabled && activity.applicationInfo.enabled &&
            (activity.permission.isNullOrEmpty() ||
                context.checkSelfPermission(activity.permission) == PackageManager.PERMISSION_GRANTED)
    }.getOrDefault(false)

    private fun startActivity(context: Context, intent: Intent): Boolean = try {
        val launchIntent = Intent(intent)
        if (context !is Activity) launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        true
    } catch (exception: Exception) {
        logE("打开后台设置失败: ${exception.javaClass.simpleName}")
        false
    }

    @StringRes
    private fun batteryMessage(manufacturer: Manufacturer): Int = when (manufacturer) {
        Manufacturer.XIAOMI -> R.string.compatibility_battery_xiaomi
        Manufacturer.HUAWEI -> R.string.compatibility_battery_huawei
        Manufacturer.OPPO -> R.string.compatibility_battery_oppo
        Manufacturer.VIVO -> R.string.compatibility_battery_vivo
        Manufacturer.SAMSUNG -> R.string.compatibility_battery_samsung
        Manufacturer.OTHER -> R.string.compatibility_battery_default
    }

    @StringRes
    private fun autoStartMessage(manufacturer: Manufacturer): Int = when (manufacturer) {
        Manufacturer.XIAOMI -> R.string.compatibility_auto_start_xiaomi
        Manufacturer.HUAWEI -> R.string.compatibility_auto_start_huawei
        Manufacturer.OPPO -> R.string.compatibility_auto_start_oppo
        Manufacturer.VIVO -> R.string.compatibility_auto_start_vivo
        else -> error("该厂商无自启动专属引导")
    }

    @StringRes
    private fun popupMessage(manufacturer: Manufacturer): Int = when (manufacturer) {
        Manufacturer.XIAOMI -> R.string.compatibility_popup_xiaomi
        Manufacturer.HUAWEI -> R.string.compatibility_popup_huawei
        Manufacturer.OPPO -> R.string.compatibility_popup_oppo
        Manufacturer.VIVO -> R.string.compatibility_popup_vivo
        else -> error("该厂商无后台弹页专属引导")
    }
}

data class PermissionGuideItem(
    val id: PermissionGuideId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    val settingsIntent: Intent,
    @param:StringRes val statusRes: Int? = null,
)

enum class PermissionGuideId { BATTERY_OPTIMIZATION, AUTO_START, MANUFACTURER_POPUP }
