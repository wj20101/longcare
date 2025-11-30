package com.ytone.longcare.common.utils

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat

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
            return "相机权限未授予"
        }
        
        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return "设备不支持相机功能"
        }
        
        return "相机功能正常"
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

    /**
     * 获取定位相关需要请求的权限列表
     */
    fun getLocationRequiredPermissions(): Array<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return permissions.toTypedArray()
    }

    /**
     * 引导用户开启定位服务
     */
    fun openLocationSettings(context: Context) {
        Toast.makeText(context, "请先在系统设置中开启定位服务", Toast.LENGTH_LONG).show()
        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    /**
     * 处理定位权限请求结果
     */
    fun handleLocationPermissionResult(
        permissions: Map<String, Boolean>,
        context: Context,
        onPermissionGranted: () -> Unit
    ) {
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // 权限获取成功，执行回调
            onPermissionGranted()
        } else {
            Toast.makeText(context, "需要定位权限才能开始服务", Toast.LENGTH_LONG).show()
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

// ==================== Composable 函数 ====================

/**
 * Composable函数：创建定位权限请求启动器
 */
@Composable
fun rememberLocationPermissionLauncher(
    onPermissionGranted: () -> Unit
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    val context = LocalContext.current
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            UnifiedPermissionHelper.handleLocationPermissionResult(
                permissions = permissions,
                context = context,
                onPermissionGranted = onPermissionGranted
            )
        }
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