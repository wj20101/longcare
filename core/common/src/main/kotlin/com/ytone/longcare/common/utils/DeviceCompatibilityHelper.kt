package com.ytone.longcare.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.annotation.StringRes
import com.ytone.longcare.core.common.R

/**
 * 设备兼容性辅助工具
 * 针对不同厂商 ROM 提供适配引导
 */
object DeviceCompatibilityHelper {
    
    private const val PREFS_NAME = "device_compatibility_prefs"
    private const val KEY_MANUFACTURER_GUIDE_SHOWN = "manufacturer_guide_shown"
    private const val KEY_AUTO_START_GUIDE_SHOWN = "auto_start_guide_shown"
    
    /**
     * 获取设备厂商
     */
    private fun getManufacturer(): String = Build.MANUFACTURER.lowercase()
    
    /**
     * 是否为华为/荣耀设备
     */
    private fun isHuawei(): Boolean =
        getManufacturer() in listOf("huawei", "honor")
    
    /**
     * 是否为小米/红米设备
     */
    private fun isXiaomi(): Boolean =
        getManufacturer() in listOf("xiaomi", "redmi")
    
    /**
     * 是否为 OPPO/realme 设备
     */
    private fun isOppo(): Boolean =
        getManufacturer() in listOf("oppo", "realme")
    
    /**
     * 是否为 vivo 设备
     */
    private fun isVivo(): Boolean =
        getManufacturer() == "vivo"
    
    /** 是否需要厂商专属设置引导。 */
    private fun needsSpecialAdaptation(): Boolean =
        isHuawei() || isXiaomi() || isOppo() || isVivo()
    
    /**
     * 检查是否已获取后台弹出界面权限
     * 参考 BGStart 库实现
     * @return true 表示已获取权限，false 表示未获取或无法检测
     */
    private fun hasBgStartPermission(context: Context): Boolean {
        return try {
            when {
                isXiaomi() -> checkXiaomiBgStartPermission(context)
                isVivo() -> checkVivoBgStartPermission(context)
                isOppo() -> checkOppoBgStartPermission(context)
                isHuawei() -> checkHuaweiBgStartPermission(context)
                else -> true // 非特殊厂商默认有权限
            }
        } catch (e: Exception) {
            // Assuming logE is a defined logging function, otherwise replace with standard logging
            // For example: Log.e("DeviceCompatibilityHelper", "检查后台弹出权限失败: ${e.message}")
            logE("检查后台弹出权限失败: ${e.message}")
            true // 检查失败时默认有权限，避免影响用户体验
        }
    }
    
    /**
     * 小米：通过 AppOpsManager 检查 opCode 10021
     */
    private fun checkXiaomiBgStartPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService<AppOpsManager>() ?: return true
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.java,
                Int::class.java,
                String::class.java
            )
            // 小米后台弹出界面权限 opCode = 10021
            val result = method.invoke(appOps, 10021, Binder.getCallingUid(), context.packageName) as Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            logE("小米权限检查失败: ${e.message}")
            true // 失败时默认有权限
        }
    }
    
    /**
     * vivo：通过 ContentProvider 检查
     */
    private fun checkVivoBgStartPermission(context: Context): Boolean {
        return try {
            val uri = "content://com.vivo.permissionmanager.provider.permission/start_bg_activity".toUri()
            val cursor = context.contentResolver.query(uri, null, "pkgname = ?", arrayOf(context.packageName), null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val state = it.getInt(it.getColumnIndexOrThrow("currentstate"))
                    return state == 0 // 0 表示允许
                }
            }
            true // 查询失败默认有权限
        } catch (e: Exception) {
            logE("vivo权限检查失败: ${e.message}")
            true
        }
    }
    
    /**
     * OPPO：通过 AppOpsManager 检查 OP_BACKGROUND_START_ACTIVITY
     */
    private fun checkOppoBgStartPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService<AppOpsManager>() ?: return true
            // OPPO 使用标准的 OP_BACKGROUND_START_ACTIVITY (66)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val method = appOps.javaClass.getMethod(
                    "checkOpNoThrow",
                    Int::class.java,
                    Int::class.java,
                    String::class.java
                )
                val result = method.invoke(appOps, 66, Binder.getCallingUid(), context.packageName) as Int
                result == AppOpsManager.MODE_ALLOWED
            } else {
                true
            }
        } catch (e: Exception) {
            logE("OPPO权限检查失败: ${e.message}")
            true
        }
    }
    
    /**
     * 华为：通过 AppOpsManager 检查
     */
    private fun checkHuaweiBgStartPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService<AppOpsManager>() ?: return true
            val method = appOps.javaClass.getMethod(
                "checkOpNoThrow",
                Int::class.java,
                Int::class.java,
                String::class.java
            )
            // 华为后台弹窗权限 opCode = 100000 (不同版本可能不同)
            val result = method.invoke(appOps, 100000, Binder.getCallingUid(), context.packageName) as Int
            result == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            logE("华为权限检查失败: ${e.message}")
            true // 失败时默认有权限
        }
    }
    
    /**
     * 获取电池优化设置 Intent
     * 针对不同 Android 版本和厂商提供正确的 Intent
     */
    private fun getBatteryOptimizationIntent(context: Context): Intent {
        // 优先尝试厂商特定的设置页面
        val manufacturerIntent = getManufacturerBatteryIntent(context)
        if (manufacturerIntent != null) {
            return manufacturerIntent
        }
        
        // 通用 Android 6.0+ 电池优化设置
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    }
    
    /**
     * 获取厂商特定的电池优化 Intent
     * 如果厂商没有特定设置或 Activity 不存在，返回 null
     */
    private fun getManufacturerBatteryIntent(context: Context): Intent? {
        val intent = when {
            isXiaomi() -> Intent().apply {
                setClassName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                )
                putExtra("package_name", context.packageName)
                putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
            }
            isHuawei() -> Intent().apply {
                setClassName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                )
            }
            isOppo() -> Intent().apply {
                setClassName(
                    "com.coloros.oppoguardelf",
                    "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                )
            }
            isVivo() -> Intent().apply {
                setClassName(
                    "com.vivo.abe",
                    "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                )
            }
            else -> null
        }
        
        // 验证 Intent 对应的 Activity 是否存在
        return intent?.takeIf { 
            context.packageManager.resolveActivity(it, 0) != null 
        }
    }
    
    /**
     * 检查应用是否已加入电池优化白名单
     */
    private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService<PowerManager>()
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
    }
    
    /**
     * 获取自启动设置 Intent
     */
    private fun getAutoStartIntent(context: Context): Intent? {
        return when {
            isHuawei() -> Intent().apply {
                try {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
                    )
                } catch (e: Exception) {
                    return null
                }
            }
            isXiaomi() -> Intent("miui.intent.action.OP_AUTO_START").apply {
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            isOppo() -> Intent().apply {
                try {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"
                    )
                } catch (e: Exception) {
                    return null
                }
            }
            isVivo() -> Intent().apply {
                try {
                    setClassName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
                    )
                } catch (e: Exception) {
                    return null
                }
            }
            else -> null
        }
    }
    
    /**
     * 获取后台弹出界面权限设置 Intent（小米特有）
     * 这是小米设备上 fullScreenIntent 不工作的核心原因
     */
    private fun getBackgroundPopupIntent(context: Context): Intent? {
        if (!isXiaomi()) return null
        
        // 小米/MIUI 备选 Activity 列表（不同 MIUI 版本有不同的 Activity）
        // 方法 1: MIUI 权限编辑页面（较新版本）
        createComponentIntentIfAvailable(
            context,
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity",
            mapOf("extra_pkgname" to context.packageName)
        )?.let { return it }
        
        // 方法 2: MIUI 应用权限页面
        createComponentIntentIfAvailable(
            context,
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
            mapOf("extra_pkgname" to context.packageName)
        )?.let { return it }
        
        // 方法 3: 尝试使用 action 启动
        Intent("miui.intent.action.APP_PERM_EDITOR").apply {
            putExtra("extra_pkgname", context.packageName)
        }.takeIf { isActivityAvailable(context, it) }?.let { return it }
        
        // 方法 4: 回退到系统应用设置页
        return null
    }
    
    /**
     * 获取通用应用设置 Intent
     */
    private fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
    
    /**
     * 获取弹窗权限提示信息（第一步）
     * 包含「锁屏显示」和「后台弹出界面」权限
     */
    @StringRes
    private fun getPopupPermissionGuideMessageRes(): Int? = when {
        isXiaomi() -> R.string.compatibility_popup_xiaomi
        isHuawei() -> R.string.compatibility_popup_huawei
        isOppo() -> R.string.compatibility_popup_oppo
        isVivo() -> R.string.compatibility_popup_vivo
        else -> null
    }
    
    /**
     * 获取省电策略提示信息（适用于所有设备）
     */
    @StringRes
    private fun getBatteryGuideMessageRes(): Int = when {
        isXiaomi() -> R.string.compatibility_battery_xiaomi
        isHuawei() -> R.string.compatibility_battery_huawei
        isOppo() -> R.string.compatibility_battery_oppo
        isVivo() -> R.string.compatibility_battery_vivo
        else -> R.string.compatibility_battery_default
    }

    @StringRes
    private fun getAutoStartGuideMessageRes(): Int = when {
        isXiaomi() -> R.string.compatibility_auto_start_xiaomi
        isHuawei() -> R.string.compatibility_auto_start_huawei
        isOppo() -> R.string.compatibility_auto_start_oppo
        isVivo() -> R.string.compatibility_auto_start_vivo
        else -> R.string.compatibility_auto_start_default
    }

    /**
     * 检查 Intent 对应的 Activity 是否存在
     */
    private fun isActivityAvailable(context: Context, intent: Intent): Boolean {
        return try {
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 创建指定组件的 Intent（如果存在）
     */
    private fun createComponentIntentIfAvailable(
        context: Context,
        packageName: String,
        className: String,
        extras: Map<String, String> = emptyMap()
    ): Intent? {
        val intent = Intent().apply {
            setClassName(packageName, className)
            extras.forEach { (key, value) -> putExtra(key, value) }
        }
        return if (isActivityAvailable(context, intent)) intent else null
    }
    
    /**
     * 获取弹窗权限设置 Intent（统一各厂商）
     * 每个厂商尝试多个可能的 Activity，直到找到可用的
     */
    private fun getPopupPermissionIntent(context: Context): Intent {
        return when {
            isXiaomi() -> getBackgroundPopupIntent(context) ?: getAppSettingsIntent(context)
            isHuawei() -> {
                // 华为备选 Activity 列表
                createComponentIntentIfAvailable(
                    context,
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.SingleAppActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity"
                ) ?: getAppSettingsIntent(context)
            }
            isOppo() -> {
                // OPPO/realme/ColorOS 备选 Activity 列表（不同版本有不同的 Activity）
                createComponentIntentIfAvailable(
                    context,
                    "com.coloros.safecenter",
                    "com.coloros.privacypermissionsentry.PermissionDetailActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionAppAllPermissionActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.PermissionAppAllPermissionActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.singlepage.PermissionSinglePageActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.oppo.safe",
                    "com.oppo.safe.permission.PermissionAppAllPermissionActivity",
                    mapOf("packageName" to context.packageName)
                ) ?: getAppSettingsIntent(context)
            }
            isVivo() -> {
                // vivo 备选 Activity 列表
                createComponentIntentIfAvailable(
                    context,
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity",
                    mapOf("packagename" to context.packageName)
                ) ?: createComponentIntentIfAvailable(
                    context,
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity",
                    mapOf("packagename" to context.packageName)
                ) ?: getAppSettingsIntent(context)
            }
            else -> getAppSettingsIntent(context)
        }
    }
    /**
     * 安全启动 Intent，失败时回退到通用设置
     */
    private fun safeStartActivity(context: Context, intent: Intent): Boolean {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            logE("启动厂商设置页面失败: ${e.message}")
            try {
                context.startActivity(getAppSettingsIntent(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                true
            } catch (e2: Exception) {
                logE("启动通用设置页面也失败: ${e2.message}")
                false
            }
        }
    }

    /**
     * 一次性收集所有需要引导的权限（电池 + 弹窗/悬浮窗），用于统一引导页。
     */
    fun getAllRequiredGuides(context: Context): List<PermissionGuideItem> {
        val items = mutableListOf<PermissionGuideItem>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 电池优化
        if (!isIgnoringBatteryOptimizations(context)) {
            items += PermissionGuideItem(
                id = PermissionGuideId.BATTERY_OPTIMIZATION,
                titleRes = R.string.compatibility_battery_title_power_strategy,
                messageRes = getBatteryGuideMessageRes(),
                settingsIntent = getBatteryOptimizationIntent(context),
            )
        }

        // 2. 自启动（仅特殊厂商）
        if (needsSpecialAdaptation()) {
            val autoStartIntent = getAutoStartIntent(context)
            val autoStartGuideShown = prefs.getBoolean(KEY_AUTO_START_GUIDE_SHOWN, false)
            if (autoStartIntent != null && !autoStartGuideShown) {
                items += PermissionGuideItem(
                    id = PermissionGuideId.AUTO_START,
                    titleRes = R.string.compatibility_auto_start_title,
                    messageRes = getAutoStartGuideMessageRes(),
                    settingsIntent = autoStartIntent
                )
            }
        }

        // 3. 特殊厂商后台弹窗权限。标准 Android 设备只使用全屏通知权限，
        // 不再申请高风险的悬浮窗权限作为兜底。
        if (needsSpecialAdaptation()) {
            if (!hasBgStartPermission(context)) {
                val shown = prefs.getBoolean(KEY_MANUFACTURER_GUIDE_SHOWN, false)
                if (!shown) {
                    items += PermissionGuideItem(
                        id = PermissionGuideId.MANUFACTURER_POPUP,
                        titleRes = R.string.compatibility_popup_permission_title,
                        messageRes = requireNotNull(getPopupPermissionGuideMessageRes()),
                        settingsIntent = getPopupPermissionIntent(context)
                    )
                }
            }
        }

        return items
    }

    fun openGuide(context: Context, item: PermissionGuideItem): Boolean {
        val opened = safeStartActivity(context, item.settingsIntent)
        if (opened) {
            markGuideHandled(context, item.id)
        }
        return opened
    }

    /**
     * 厂商权限通常无法可靠查询，展示一次后记为已处理；电池优化可由系统状态直接判断。
     */
    fun markGuideHandled(context: Context, guideId: PermissionGuideId) {
        val key = when (guideId) {
            PermissionGuideId.BATTERY_OPTIMIZATION -> return
            PermissionGuideId.AUTO_START -> KEY_AUTO_START_GUIDE_SHOWN
            PermissionGuideId.MANUFACTURER_POPUP -> KEY_MANUFACTURER_GUIDE_SHOWN
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit { putBoolean(key, true) }
    }
}

/**
 * 统一权限引导项
 */
data class PermissionGuideItem(
    val id: PermissionGuideId,
    @param:StringRes val titleRes: Int,
    @param:StringRes val messageRes: Int,
    val settingsIntent: Intent
)

enum class PermissionGuideId {
    BATTERY_OPTIMIZATION,
    AUTO_START,
    MANUFACTURER_POPUP,
}
