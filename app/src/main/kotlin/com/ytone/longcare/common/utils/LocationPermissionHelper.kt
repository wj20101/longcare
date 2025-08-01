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
import com.ytone.longcare.features.location.viewmodel.LocationTrackingViewModel

/**
 * 定位权限管理工具类
 * 提供统一的定位权限检查和请求功能
 */
object LocationPermissionHelper {

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
     * 获取需要请求的权限列表
     */
    fun getRequiredPermissions(): Array<String> {
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
     * 处理权限请求结果
     */
    fun handlePermissionResult(
        permissions: Map<String, Boolean>,
        context: Context,
        locationTrackingViewModel: LocationTrackingViewModel,
        orderId: Long
    ) {
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            // 权限获取成功，启动定位服务
            locationTrackingViewModel.onStartClicked(orderId)
        } else {
            Toast.makeText(context, "需要定位权限才能开始服务", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * 检查定位权限和服务
     */
    fun checkLocationPermissionAndStart(
        context: Context,
        permissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>
    ) {
        if (!isLocationServiceEnabled(context)) {
            openLocationSettings(context)
            return
        }
        permissionLauncher.launch(getRequiredPermissions())
    }
}

/**
 * Composable函数：创建定位权限请求启动器
 */
@Composable
fun rememberLocationPermissionLauncher(
    locationTrackingViewModel: LocationTrackingViewModel,
    orderId: Long
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    val context = LocalContext.current
    
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            LocationPermissionHelper.handlePermissionResult(
                permissions = permissions,
                context = context,
                locationTrackingViewModel = locationTrackingViewModel,
                orderId = orderId
            )
        }
    )
}

/**
 * Composable函数：检查定位权限和服务
 */
@Composable
fun CheckLocationPermissionAndStart(
    locationTrackingViewModel: LocationTrackingViewModel,
    orderId: Long,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLocationPermissionLauncher(
        locationTrackingViewModel = locationTrackingViewModel,
        orderId = orderId
    )
    
    LocationPermissionHelper.checkLocationPermissionAndStart(context, permissionLauncher)
}