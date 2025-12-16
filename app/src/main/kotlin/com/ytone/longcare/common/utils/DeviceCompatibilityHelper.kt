package com.ytone.longcare.common.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import androidx.core.content.edit

/**
 * 设备兼容性辅助工具
 * 针对不同厂商 ROM 提供适配引导
 */
object DeviceCompatibilityHelper {
    
    private const val PREFS_NAME = "device_compatibility_prefs"
    private const val KEY_DEVICE_GUIDE_SHOWN = "device_guide_shown"
    
    /**
     * 获取设备厂商
     */
    fun getManufacturer(): String = Build.MANUFACTURER.lowercase()
    
    /**
     * 是否为华为/荣耀设备
     */
    fun isHuawei(): Boolean = 
        getManufacturer() in listOf("huawei", "honor")
    
    /**
     * 是否为小米/红米设备
     */
    fun isXiaomi(): Boolean = 
        getManufacturer() in listOf("xiaomi", "redmi")
    
    /**
     * 是否为 OPPO/realme 设备
     */
    fun isOppo(): Boolean = 
        getManufacturer() in listOf("oppo", "realme")
    
    /**
     * 是否为 vivo 设备
     */
    fun isVivo(): Boolean = 
        getManufacturer() == "vivo"
    
    /**
     * 是否为三星设备
     */
    fun isSamsung(): Boolean = 
        getManufacturer() == "samsung"
    
    /**
     * 是否为需要特殊适配的厂商设备
     */
    fun needsSpecialAdaptation(): Boolean =
        isHuawei() || isXiaomi() || isOppo() || isVivo()
    
    /**
     * 获取电池优化设置 Intent
     */
    fun getBatteryOptimizationIntent(context: Context): Intent {
        return when {
            isHuawei() -> Intent().apply {
                try {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"
                    )
                } catch (e: Exception) {
                    // 回退到通用设置
                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            }
            isXiaomi() -> Intent().apply {
                try {
                    setClassName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", context.packageName)
                    putExtra("package_label", context.applicationInfo.loadLabel(context.packageManager))
                } catch (e: Exception) {
                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            }
            isOppo() -> Intent().apply {
                try {
                    setClassName(
                        "com.coloros.oppoguardelf",
                        "com.coloros.powermanager.fuelgaue.PowerConsumptionActivity"
                    )
                } catch (e: Exception) {
                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            }
            isVivo() -> Intent().apply {
                try {
                    setClassName(
                        "com.vivo.abe",
                        "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"
                    )
                } catch (e: Exception) {
                    action = Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                }
            }
            else -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        }
    }
    
    /**
     * 获取自启动设置 Intent
     */
    fun getAutoStartIntent(context: Context): Intent? {
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
     * 获取通用应用设置 Intent
     */
    fun getAppSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
    
    /**
     * 获取针对当前设备的提示信息
     */
    fun getDeviceSpecificGuideMessage(): String? {
        return when {
            isHuawei() -> """
                检测到您使用的是华为/荣耀手机，为保证服务结束时能准时提醒，请：
                
                1. 进入「设置 → 应用 → 应用启动管理」
                2. 找到本应用，关闭「自动管理」
                3. 手动开启「允许自启动」和「允许后台活动」
            """.trimIndent()
            isXiaomi() -> """
                检测到您使用的是小米/红米手机，为保证服务结束时能准时提醒，请：
                
                1. 进入「设置 → 应用设置 → 应用管理」
                2. 找到本应用 → 省电策略 → 选择「无限制」
                3. 开启「自启动」权限
            """.trimIndent()
            isOppo() -> """
                检测到您使用的是 OPPO 手机，为保证服务结束时能准时提醒，请：
                
                1. 进入「设置 → 电池 → 更多电池设置」
                2. 关闭「休眠时快速耗电检测」
                3. 找到本应用，设为「不优化」
            """.trimIndent()
            isVivo() -> """
                检测到您使用的是 vivo 手机，为保证服务结束时能准时提醒，请：
                
                1. 进入「i管家 → 应用管理 → 权限管理」
                2. 找到本应用，开启「后台弹窗」和「自启动」
            """.trimIndent()
            else -> null
        }
    }
    
    /**
     * 获取厂商名称（用于显示）
     */
    fun getManufacturerDisplayName(): String {
        return when {
            isHuawei() -> "华为/荣耀"
            isXiaomi() -> "小米/红米"
            isOppo() -> "OPPO/realme"
            isVivo() -> "vivo"
            isSamsung() -> "三星"
            else -> Build.MANUFACTURER
        }
    }
    
    /**
     * 检查是否已显示过设备引导
     */
    fun hasShownDeviceGuide(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_DEVICE_GUIDE_SHOWN, false)
    }
    
    /**
     * 标记已显示设备引导
     */
    fun markDeviceGuideShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_DEVICE_GUIDE_SHOWN, true) }
    }
    
    /**
     * 重置设备引导显示状态（用于测试）
     */
    fun resetDeviceGuideShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { remove(KEY_DEVICE_GUIDE_SHOWN) }
    }
    
    /**
     * 安全启动 Intent，失败时回退到通用设置
     */
    fun safeStartActivity(context: Context, intent: Intent): Boolean {
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
}
