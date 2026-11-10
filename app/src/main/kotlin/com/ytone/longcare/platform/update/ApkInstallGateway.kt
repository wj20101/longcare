package com.ytone.longcare.platform.update

import android.content.Context
import com.ytone.longcare.common.utils.ApkInstallUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstallGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun canInstallApk(): Boolean = ApkInstallUtils.canInstallApk(context)

    fun installApk(filePath: String): ApkInstallUtils.LaunchResult =
        ApkInstallUtils.installApk(context, filePath)

    fun requestInstallPermission(): ApkInstallUtils.LaunchResult =
        ApkInstallUtils.requestInstallPermission(context)

    fun openForManualInstall(filePath: String): ApkInstallUtils.LaunchResult =
        ApkInstallUtils.openApkForManualInstall(context, filePath)
}
