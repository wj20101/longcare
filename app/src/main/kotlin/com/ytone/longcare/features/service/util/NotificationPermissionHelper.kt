package com.ytone.longcare.features.service.util

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.common.utils.logI

/**
 * 通知权限帮助类
 * 处理通知权限和精确闹钟权限的请求和管理
 */
object NotificationPermissionHelper {

    private const val TAG = "NotificationPermissionHelper"
    
    // 权限请求码
    const val REQUEST_CODE_POST_NOTIFICATIONS = 1001
    const val REQUEST_CODE_SCHEDULE_EXACT_ALARM = 1002
    
    /**
     * 检查是否有所需的所有权限
     */
    fun hasAllRequiredPermissions(context: Context): Boolean {
        return hasNotificationPermission(context) && hasExactAlarmPermission(context)
    }
    
    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 13以下默认有通知权限
            true
        }
    }
    
    /**
     * 检查精确闹钟权限
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            // Android 12以下默认有精确闹钟权限
            true
        }
    }
    
    /**
     * 请求通知权限
     */
    fun requestNotificationPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!hasNotificationPermission(activity)) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    REQUEST_CODE_POST_NOTIFICATIONS
                )
            }
        }
    }
    
    /**
     * 请求精确闹钟权限
     */
    fun requestExactAlarmPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasExactAlarmPermission(activity)) {
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    intent.data = Uri.parse("package:${activity.packageName}")
                    activity.startActivityForResult(intent, REQUEST_CODE_SCHEDULE_EXACT_ALARM)
                } catch (e: Exception) {
                    logE("请求精确闹钟权限失败: ${e.message}")
                    // 降级到设置页面
                    openAppSettings(activity)
                }
            }
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    fun showPermissionRationale(activity: Activity, permissionType: String, onPositive: () -> Unit) {
        val message = when (permissionType) {
            "notification" -> "需要通知权限才能在服务时间结束时提醒您。"
            "exact_alarm" -> "需要精确闹钟权限才能确保准时触发提醒。"
            else -> "需要此权限才能正常使用提醒功能。"
        }
        
        AlertDialog.Builder(activity)
            .setTitle("权限申请")
            .setMessage(message)
            .setPositiveButton("去设置") { _, _ ->
                onPositive()
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }
    
    /**
     * 打开应用设置页面
     */
    fun openAppSettings(activity: Activity) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${activity.packageName}")
            activity.startActivity(intent)
        } catch (e: Exception) {
            logE("打开应用设置失败: ${e.message}")
        }
    }
    
    /**
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        requestCode: Int,
        grantResults: IntArray,
        onGranted: () -> Unit,
        onDenied: () -> Unit
    ) {
        when (requestCode) {
            REQUEST_CODE_POST_NOTIFICATIONS -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    logI("通知权限已授予")
                    onGranted()
                } else {
                    logE("通知权限被拒绝")
                    onDenied()
                }
            }
            REQUEST_CODE_SCHEDULE_EXACT_ALARM -> {
                // 精确闹钟权限通过ActivityResult返回，这里不处理
                onGranted()
            }
        }
    }
    
    /**
     * 检查并请求所有必需的权限
     */
    fun checkAndRequestPermissions(
        activity: Activity,
        onAllGranted: () -> Unit,
        onPermissionDenied: (String) -> Unit
    ) {
        val missingPermissions = mutableListOf<String>()
        
        // 检查通知权限
        if (!hasNotificationPermission(activity)) {
            missingPermissions.add("notification")
        }
        
        // 检查精确闹钟权限
        if (!hasExactAlarmPermission(activity)) {
            missingPermissions.add("exact_alarm")
        }
        
        if (missingPermissions.isEmpty()) {
            logI("所有权限都已授予")
            onAllGranted()
        } else {
            // 显示权限说明并请求
            val firstMissingPermission = missingPermissions.first()
            showPermissionRationale(activity, firstMissingPermission) {
                when (firstMissingPermission) {
                    "notification" -> requestNotificationPermission(activity)
                    "exact_alarm" -> requestExactAlarmPermission(activity)
                }
            }
            onPermissionDenied(firstMissingPermission)
        }
    }
}