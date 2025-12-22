package com.ytone.longcare.common.utils

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Binder
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
     * 检查是否已获取后台弹出界面权限
     * 参考 BGStart 库实现
     * @return true 表示已获取权限，false 表示未获取或无法检测
     */
    fun hasBgStartPermission(context: Context): Boolean {
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
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
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
     * 获取后台弹出界面权限设置 Intent（小米特有）
     * 这是小米设备上 fullScreenIntent 不工作的核心原因
     */
    fun getBackgroundPopupIntent(context: Context): Intent? {
        if (!isXiaomi()) return null
        
        return try {
            // 方法 1: 直接打开应用的权限设置页面
            Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
                putExtra("extra_pkgname", context.packageName)
            }
        } catch (e: Exception) {
            try {
                // 方法 2: 打开应用详情页面
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = "package:${context.packageName}".toUri()
                }
            } catch (e2: Exception) {
                null
            }
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
     * 获取弹窗权限提示信息（第一步）
     * 包含「锁屏显示」和「后台弹出界面」权限
     */
    fun getPopupPermissionGuideMessage(): String? {
        return when {
            isXiaomi() -> """
                为保证服务结束时能弹出全屏提醒，请开启以下权限：
                
                1. 进入「设置 → 应用设置 → 应用管理」
                2. 找到本应用 → 点击「其他权限」
                3. ✅ 开启「后台弹出界面」
                4. ✅ 开启「锁屏显示」
                5. ✅ 开启「显示在其他应用上层」
            """.trimIndent()
            isHuawei() -> """
                为保证服务结束时能弹出全屏提醒，请开启以下权限：
                
                1. 进入「设置 → 应用 → 应用管理」
                2. 找到本应用 → 点击「权限」
                3. ✅ 开启「后台弹窗」
                4. ✅ 开启「锁屏显示」
                5. ✅ 开启「悬浮窗」
            """.trimIndent()
            isOppo() -> """
                为保证服务结束时能弹出全屏提醒，请开启以下权限：
                
                1. 进入「设置 → 权限隐私 → 权限管理」
                2. 找到本应用
                3. ✅ 开启「后台弹出界面」
                4. ✅ 开启「锁屏界面显示」
                5. ✅ 开启「悬浮窗」
            """.trimIndent()
            isVivo() -> """
                为保证服务结束时能弹出全屏提醒，请开启以下权限：
                
                1. 进入「i管家 → 应用管理 → 权限管理」
                2. 找到本应用
                3. ✅ 开启「后台弹出界面」
                4. ✅ 开启「锁屏显示」
                5. ✅ 开启「悬浮窗」
            """.trimIndent()
            else -> null
        }
    }
    
    /**
     * 获取省电策略提示信息（第二步）
     */
    fun getBatteryGuideMessage(): String? {
        return when {
            isXiaomi() -> """
                为保证服务倒计时不被系统中断，请设置省电策略：
                
                1. 进入「设置 → 应用设置 → 应用管理」
                2. 找到本应用 → 点击「省电策略」
                3. ✅ 选择「无限制」
                4. 返回 → 开启「自启动」
            """.trimIndent()
            isHuawei() -> """
                为保证服务倒计时不被系统中断，请设置电池优化：
                
                1. 进入「设置 → 应用 → 应用启动管理」
                2. 找到本应用，关闭「自动管理」
                3. ✅ 开启「允许自启动」
                4. ✅ 开启「允许后台活动」
                5. ✅ 开启「允许关联启动」
            """.trimIndent()
            isOppo() -> """
                为保证服务倒计时不被系统中断，请设置电池优化：
                
                1. 进入「设置 → 电池 → 更多电池设置」
                2. 关闭「休眠时快速耗电检测」
                3. 进入「设置 → 电池 → 耗电管理」
                4. 找到本应用 → 设置为「不优化」
            """.trimIndent()
            isVivo() -> """
                为保证服务倒计时不被系统中断，请设置电池优化：
                
                1. 进入「设置 → 电池 → 后台耗电管理」
                2. 找到本应用 → 设置为「允许后台高耗电」
                3. 回到「设置 → 应用 → 自启动」
                4. 找到本应用 → 开启自启动
            """.trimIndent()
            else -> null
        }
    }
    
    /**
     * 获取弹窗权限设置 Intent（统一各厂商）
     */
    fun getPopupPermissionIntent(context: Context): Intent {
        return when {
            isXiaomi() -> getBackgroundPopupIntent(context) ?: getAppSettingsIntent(context)
            isHuawei() -> Intent().apply {
                try {
                    setClassName(
                        "com.huawei.systemmanager",
                        "com.huawei.permissionmanager.ui.SingleAppActivity"
                    )
                    putExtra("packageName", context.packageName)
                } catch (e: Exception) {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = "package:${context.packageName}".toUri()
                }
            }
            isOppo() -> Intent().apply {
                try {
                    setClassName(
                        "com.coloros.safecenter",
                        "com.coloros.privacypermissionsentry.PermissionDetailActivity"
                    )
                    putExtra("packageName", context.packageName)
                } catch (e: Exception) {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = "package:${context.packageName}".toUri()
                }
            }
            isVivo() -> Intent().apply {
                try {
                    setClassName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity"
                    )
                    putExtra("packagename", context.packageName)
                } catch (e: Exception) {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = "package:${context.packageName}".toUri()
                }
            }
            else -> getAppSettingsIntent(context)
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
