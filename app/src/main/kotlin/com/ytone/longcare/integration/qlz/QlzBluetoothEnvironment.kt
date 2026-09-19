package com.ytone.longcare.integration.qlz

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat

internal data class QlzBluetoothEnvironmentSnapshot(
    val hasBleFeature: Boolean,
    val permissionsGranted: Boolean,
    val hasBluetoothAdapter: Boolean,
    val bluetoothEnabled: Boolean,
    val locationServiceEnabled: Boolean,
)

internal fun evaluateQlzBluetoothEnvironment(
    snapshot: QlzBluetoothEnvironmentSnapshot,
): QlzEvaluationIssue? =
    when {
        !snapshot.hasBleFeature || !snapshot.hasBluetoothAdapter ->
            QlzEvaluationIssue.BLUETOOTH_UNSUPPORTED

        !snapshot.permissionsGranted -> QlzEvaluationIssue.PERMISSION_REQUIRED
        !snapshot.bluetoothEnabled -> QlzEvaluationIssue.BLUETOOTH_DISABLED
        !snapshot.locationServiceEnabled ->
            QlzEvaluationIssue.LOCATION_SERVICE_DISABLED

        else -> null
    }

internal fun Activity.qlzBluetoothEnvironmentIssue(
    requiredPermissions: Array<String>,
): QlzEvaluationIssue? {
    val permissionsGranted =
        requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) ==
                PackageManager.PERMISSION_GRANTED
        }
    val hasBleFeature =
        packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    val bluetoothAdapter =
        (getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    val bluetoothEnabled =
        if (!permissionsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            false
        } else {
            runCatching { bluetoothAdapter?.isEnabled == true }.getOrDefault(false)
        }
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val locationServiceEnabled = runCatching {
        locationManager?.let { manager ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                manager.isLocationEnabled
            } else {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }
        } == true
    }.getOrDefault(false)
    return evaluateQlzBluetoothEnvironment(
        QlzBluetoothEnvironmentSnapshot(
            hasBleFeature = hasBleFeature,
            permissionsGranted = permissionsGranted,
            hasBluetoothAdapter = bluetoothAdapter != null,
            bluetoothEnabled = bluetoothEnabled,
            locationServiceEnabled = locationServiceEnabled,
        )
    )
}

internal fun QlzEvaluationIssue.environmentRecoveryAction(): QlzEvaluationRecoveryAction =
    when (this) {
        QlzEvaluationIssue.PERMISSION_REQUIRED,
        QlzEvaluationIssue.BLUETOOTH_DISABLED,
        QlzEvaluationIssue.LOCATION_SERVICE_DISABLED,
        -> QlzEvaluationRecoveryAction.RECHECK_ENVIRONMENT

        else -> QlzEvaluationRecoveryAction.EXIT
    }

internal fun qlzRequiredRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            // Without neverForLocation, BLE scans also require precise foreground location.
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
