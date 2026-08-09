package com.ytone.longcare.platform.update

import android.content.Context
import com.ytone.longcare.common.utils.ApkInstallUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApkInstallGateway @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val apkPackageVerifier: ApkPackageVerifier,
) {
    fun canInstallApk(): Boolean = ApkInstallUtils.canInstallApk(context)

    fun installApk(filePath: String): ApkInstallUtils.LaunchResult =
        verified(filePath) { ApkInstallUtils.installApk(context, filePath) }

    fun requestInstallPermission(): ApkInstallUtils.LaunchResult =
        ApkInstallUtils.requestInstallPermission(context)

    fun openForManualInstall(filePath: String): ApkInstallUtils.LaunchResult =
        verified(filePath) { ApkInstallUtils.openApkForManualInstall(context, filePath) }

    private inline fun verified(
        filePath: String,
        install: () -> ApkInstallUtils.LaunchResult,
    ): ApkInstallUtils.LaunchResult = when (val verification = apkPackageVerifier.verify(File(filePath))) {
        is ApkVerificationResult.Valid -> install()
        is ApkVerificationResult.Invalid -> ApkInstallUtils.LaunchResult.Failed(verification.message)
    }
}
