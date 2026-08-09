package com.ytone.longcare.common.utils

import android.annotation.SuppressLint
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

/**
 * 设备兼容性辅助工具
 * 针对不同厂商 ROM 提供适配引导
 */
object DeviceCompatibilityHelper {
    
    private const val PREFS_NAME = "device_compatibility_prefs"
    private const val KEY_DEVICE_GUIDE_SHOWN = "device_guide_shown"
    private const val KEY_FULL_SCREEN_GUIDE_SHOWN = "full_screen_guide_shown"
    private const val KEY_MANUFACTURER_GUIDE_SHOWN = "manufacturer_guide_shown"
    private const val KEY_IGNORE_BATTERY_REQUEST_ATTEMPTED = "ignore_battery_request_attempted"
    private const val KEY_AUTO_START_GUIDE_SHOWN = "auto_start_guide_shown"
    
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
     * 标记权限引导已显示
     */
    fun markPermissionGuideShown(context: Context, guideType: PermissionGuideType) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (guideType) {
            PermissionGuideType.FULL_SCREEN_INTENT -> KEY_FULL_SCREEN_GUIDE_SHOWN
            PermissionGuideType.MANUFACTURER_POPUP -> KEY_MANUFACTURER_GUIDE_SHOWN
            PermissionGuideType.BATTERY -> return
            PermissionGuideType.NONE -> return
        }
        prefs.edit { putBoolean(key, true) }
    }

    fun getBatteryGuideStep(context: Context): BatteryGuideStep {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ignoreBatteryRequestAttempted =
            prefs.getBoolean(KEY_IGNORE_BATTERY_REQUEST_ATTEMPTED, false)
        val autoStartGuideShown = prefs.getBoolean(KEY_AUTO_START_GUIDE_SHOWN, false)
        val needsAutoStartGuide = needsSpecialAdaptation() && getAutoStartIntent(context) != null

        return resolveBatteryGuideStep(
            isIgnoringBatteryOptimizations = isIgnoringBatteryOptimizations(context),
            ignoreBatteryRequestAttempted = ignoreBatteryRequestAttempted,
            needsAutoStartGuide = needsAutoStartGuide,
            autoStartGuideShown = autoStartGuideShown,
        )
    }

    fun markIgnoreBatteryOptimizationRequestAttempted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_IGNORE_BATTERY_REQUEST_ATTEMPTED, true) }
    }

    fun markAutoStartGuideShown(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit { putBoolean(KEY_AUTO_START_GUIDE_SHOWN, true) }
    }
    
    /**
     * 获取全屏通知权限设置 Intent
     */
    fun getFullScreenIntentSettingsIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            getAppSettingsIntent(context)
        }
    }
    
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
    fun getBatteryOptimizationIntent(context: Context): Intent {
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
     * 请求免电池优化权限（会弹出系统对话框）
     * minSdk=24，始终可用
     */
    @SuppressLint("BatteryLife")
    fun getRequestIgnoreBatteryOptimizationIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
        }
    }
    
    /**
     * 检查应用是否已加入电池优化白名单
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService<PowerManager>()
        return powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
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
     * 获取省电策略提示信息（适用于所有设备）
     */
    fun getBatteryGuideMessage(): String {
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
            else -> """
                为保证服务倒计时不被系统中断，请设置电池优化：
                
                1. 进入「设置 → 电池」或「设置 → 应用管理」
                2. 找到本应用的电池/耗电设置
                3. ✅ 关闭电池优化 或 设置为「不限制后台」
                4. ✅ 允许后台运行
            """.trimIndent()
        }
    }

    fun getAutoStartGuideMessage(): String {
        return when {
            isXiaomi() -> """
                为保证后台定时通知和全屏弹窗稳定触发，请开启自启动：

                1. 进入「设置 → 应用设置 → 应用管理」
                2. 找到本应用 → 点击「自启动管理」
                3. ✅ 开启「允许自启动」
            """.trimIndent()
            isHuawei() -> """
                为保证后台定时通知和全屏弹窗稳定触发，请开启应用启动管理：

                1. 进入「设置 → 应用 → 应用启动管理」
                2. 找到本应用，关闭「自动管理」
                3. ✅ 开启「允许自启动」
                4. ✅ 开启「允许后台活动」
                5. ✅ 开启「允许关联启动」
            """.trimIndent()
            isOppo() -> """
                为保证后台定时通知和全屏弹窗稳定触发，请开启自启动：

                1. 进入「设置 → 应用管理 → 自启动管理」
                2. 找到本应用
                3. ✅ 开启「允许自启动」
                4. 如有「后台运行」相关开关，也请一并开启
            """.trimIndent()
            isVivo() -> """
                为保证后台定时通知和全屏弹窗稳定触发，请开启自启动：

                1. 进入「设置 → 应用与权限 → 权限管理 → 自启动」
                2. 找到本应用
                3. ✅ 开启「允许自启动」
                4. 如有「后台高耗电」或「后台弹出界面」，也请允许
            """.trimIndent()
            else -> """
                为保证后台定时通知和全屏弹窗稳定触发，请允许本应用后台自启动和后台运行。
            """.trimIndent()
        }
    }

    fun getBatteryGuideDialogTitle(step: BatteryGuideStep): String {
        return when (step) {
            BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> "关闭电池优化"
            BatteryGuideStep.OPEN_BATTERY_SETTINGS -> "设置省电策略"
            BatteryGuideStep.OPEN_AUTO_START_SETTINGS -> "开启自启动"
            BatteryGuideStep.NONE -> ""
        }
    }

    fun getBatteryGuideConfirmLabel(step: BatteryGuideStep): String {
        return when (step) {
            BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> "去授权"
            BatteryGuideStep.OPEN_BATTERY_SETTINGS,
            BatteryGuideStep.OPEN_AUTO_START_SETTINGS -> "去设置"
            BatteryGuideStep.NONE -> "知道了"
        }
    }

    fun getBatteryGuideMessage(context: Context, step: BatteryGuideStep): String {
        return when (step) {
            BatteryGuideStep.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS -> """
                为保证后台定时通知和全屏弹窗不被系统省电策略拦截，请先将本应用加入“不受电池优化限制”名单。

                点击「去授权」后，请在系统弹窗中选择「允许」。
            """.trimIndent()
            BatteryGuideStep.OPEN_BATTERY_SETTINGS -> getBatteryGuideMessage()
            BatteryGuideStep.OPEN_AUTO_START_SETTINGS ->
                if (getAutoStartIntent(context) != null) getAutoStartGuideMessage() else getBatteryGuideMessage()
            BatteryGuideStep.NONE -> ""
        }
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
    fun getPopupPermissionIntent(context: Context): Intent {
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

    /**
     * 一次性收集所有需要引导的权限（电池 + 弹窗/悬浮窗），用于统一引导页。
     */
    fun getAllRequiredGuides(context: Context): List<PermissionGuideItem> {
        val items = mutableListOf<PermissionGuideItem>()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 电池优化
        if (!isIgnoringBatteryOptimizations(context)) {
            val attempted = prefs.getBoolean(KEY_IGNORE_BATTERY_REQUEST_ATTEMPTED, false)
            items += PermissionGuideItem(
                type = PermissionGuideType.BATTERY,
                title = if (attempted) "设置省电策略" else "关闭电池优化",
                message = if (attempted) getBatteryGuideMessage() else "请将本应用加入「不受电池优化限制」名单，保证后台服务不被中断。",
                isGranted = false,
                settingsIntent = if (attempted) getBatteryOptimizationIntent(context) else getRequestIgnoreBatteryOptimizationIntent(context)
            )
        }

        // 2. 自启动（仅特殊厂商）
        if (needsSpecialAdaptation()) {
            val autoStartIntent = getAutoStartIntent(context)
            val autoStartGuideShown = prefs.getBoolean(KEY_AUTO_START_GUIDE_SHOWN, false)
            if (autoStartIntent != null && !autoStartGuideShown) {
                items += PermissionGuideItem(
                    type = PermissionGuideType.BATTERY, // 归属电池类
                    title = "开启自启动",
                    message = getAutoStartGuideMessage(),
                    isGranted = false,
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
                        type = PermissionGuideType.MANUFACTURER_POPUP,
                        title = "开启弹窗权限",
                        message = getPopupPermissionGuideMessage().orEmpty(),
                        isGranted = false,
                        settingsIntent = getPopupPermissionIntent(context)
                    )
                }
            }
        }

        return items
    }
}

/**
 * 统一权限引导项
 */
data class PermissionGuideItem(
    val type: PermissionGuideType,
    val title: String,
    val message: String,
    val isGranted: Boolean,
    val settingsIntent: Intent
)
