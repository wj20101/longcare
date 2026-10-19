package com.ytone.longcare.common.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.ytone.longcare.core.ui.R

/**
 * 统一权限管理工具类
 * 提供应用所需的各种权限检查和请求功能
 */
object UnifiedPermissionHelper {

    // ==================== 相机权限相关 ====================
    
    /**
     * 检查相机权限是否已授予
     * @param context 上下文
     * @return 如果权限已授予返回true，否则返回false
     */
    fun isCameraPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查写入外部存储权限是否已授予
     * @param context 上下文
     * @return 如果权限已授予返回true，否则返回false
     */
    fun isWriteExternalStoragePermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查相机功能是否可用
     * 包括权限检查和硬件检查
     * @param context 上下文
     * @return 如果相机功能可用返回true，否则返回false
     */
    fun isCameraAvailable(context: Context): Boolean {
        // 检查权限
        if (!isCameraPermissionGranted(context)) {
            return false
        }
        
        // 检查硬件
        val packageManager = context.packageManager
        return packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }
    
    /**
     * 获取相机不可用的原因
     * @param context 上下文
     * @return 返回相机不可用的具体原因
     */
    fun getCameraUnavailableReason(context: Context): String {
        if (!isCameraPermissionGranted(context)) {
            return context.getString(R.string.camera_permission_not_granted)
        }
        
        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return context.getString(R.string.camera_feature_unsupported)
        }
        
        return context.getString(R.string.camera_feature_available)
    }

    // ==================== 定位权限相关 ====================

    /**
     * 检查定位服务是否开启
     */
    fun isLocationServiceEnabled(context: Context): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return LocationManagerCompat.isLocationEnabled(locationManager)
    }

    /**
     * 检查是否有定位权限
     */
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Android 12+ 必须一并请求精确与大致位置；通知在提醒业务中单独申请。 */
    fun getLocationRequiredPermissions(): Array<String> = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /**
     * 引导用户开启定位服务
     */
    fun openLocationSettings(context: Context) {
        Toast.makeText(
            context,
            context.getString(R.string.location_service_open_settings),
            Toast.LENGTH_LONG,
        ).show()
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    /**
     * 处理定位权限请求结果
     */
    fun handleLocationPermissionResult(
        context: Context,
        onPermissionGranted: () -> Unit,
        onPermissionDenied: () -> Unit = {}
    ) {
        if (hasLocationPermission(context)) {
            // 权限获取成功，执行回调
            onPermissionGranted()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.location_permission_denied_message),
                Toast.LENGTH_LONG,
            ).show()
            onPermissionDenied()
        }
    }

    /**
     * 检查定位权限和服务
     * 如果已有权限，直接执行回调；否则请求权限
     */
    fun checkLocationPermissionAndStart(
        context: Context,
        permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
        onPermissionGranted: () -> Unit
    ) {
        if (!isLocationServiceEnabled(context)) {
            openLocationSettings(context)
            return
        }
        
        if (hasLocationPermission(context)) {
            // 已有权限，直接执行回调
            onPermissionGranted()
        } else {
            // 请求权限
            permissionLauncher.launch(getLocationRequiredPermissions())
        }
    }

    // ==================== 通用权限检查 ====================

    /**
     * 检查单个权限是否已授予
     * @param context 上下文
     * @param permission 权限名称
     * @return 如果权限已授予返回true，否则返回false
     */
    fun isPermissionGranted(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查多个权限是否都已授予
     * @param context 上下文
     * @param permissions 权限列表
     * @return 如果所有权限都已授予返回true，否则返回false
     */
    fun arePermissionsGranted(context: Context, permissions: Array<String>): Boolean {
        return permissions.all { permission ->
            isPermissionGranted(context, permission)
        }
    }

    /**
     * 获取未授予的权限列表
     * @param context 上下文
     * @param permissions 权限列表
     * @return 未授予的权限列表
     */
    fun getDeniedPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter { permission ->
            !isPermissionGranted(context, permission)
        }
    }
}

data class PermissionPurposeNotice(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val cancelLabel: String,
)

fun cameraPermissionPurposeNotice(
    context: Context,
    featureName: String,
): PermissionPurposeNotice {
    return PermissionPurposeNotice(
        title = context.getString(R.string.permission_purpose_camera_title),
        message = context.getString(R.string.permission_purpose_camera_message, featureName),
        confirmLabel = context.getString(R.string.permission_purpose_continue),
        cancelLabel = context.getString(R.string.common_cancel),
    )
}

@Composable
fun cameraPermissionPurposeNotice(featureName: String): PermissionPurposeNotice =
    cameraPermissionPurposeNotice(LocalContext.current, featureName)

fun locationPermissionPurposeNotice(
    context: Context,
    featureName: String,
): PermissionPurposeNotice {
    return PermissionPurposeNotice(
        title = context.getString(R.string.permission_purpose_location_title),
        message = context.getString(R.string.permission_purpose_location_message, featureName),
        confirmLabel = context.getString(R.string.permission_purpose_continue),
        cancelLabel = context.getString(R.string.common_cancel),
    )
}

@Composable
fun locationPermissionPurposeNotice(featureName: String): PermissionPurposeNotice =
    locationPermissionPurposeNotice(LocalContext.current, featureName)

@Composable
fun PermissionPurposeDialog(
    notice: PermissionPurposeNotice,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(notice.title) },
        text = {
            Column {
                Text(notice.message)
                if (onOpenSettings != null) {
                    TextButton(onClick = onOpenSettings) {
                        Text(stringResource(R.string.permission_purpose_settings))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(notice.confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(notice.cancelLabel)
            }
        }
    )
}

// ==================== Composable 函数 ====================

/**
 * Composable函数：创建定位权限请求启动器
 */
@Composable
fun rememberLocationPermissionLauncher(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            // 只读取系统真实授权并回传业务结果；拒绝后不再自动弹出设置劝导。
            UnifiedPermissionHelper.handleLocationPermissionResult(
                context = context,
                onPermissionGranted = onPermissionGranted,
                onPermissionDenied = onPermissionDenied,
            )
        },
    )
}

/**
 * Composable函数：创建通用权限请求启动器
 */
@Composable
fun rememberPermissionLauncher(
    onResult: (Map<String, Boolean>) -> Unit
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = onResult
    )
}

/**
 * Composable函数：创建单个权限请求启动器
 */
@Composable
fun rememberSinglePermissionLauncher(
    onResult: (Boolean) -> Unit
): ManagedActivityResultLauncher<String, Boolean> {
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = onResult
    )
}
