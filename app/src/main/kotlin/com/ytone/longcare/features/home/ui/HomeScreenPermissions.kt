package com.ytone.longcare.features.home.ui

import android.Manifest
import android.os.Build

internal fun buildRequiredPermissions(): List<String> {
    val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.CAMERA
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
    }
    return requiredPermissions
}
