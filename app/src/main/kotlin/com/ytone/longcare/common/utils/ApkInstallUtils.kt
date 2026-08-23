package com.ytone.longcare.common.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import com.ytone.longcare.R
import java.io.File

/**
 * APK安装工具类
 */
object ApkInstallUtils {

    sealed interface LaunchResult {
        data object Launched : LaunchResult
        data class ManualFallback(val message: String) : LaunchResult
        data class Failed(val message: String) : LaunchResult
    }

    /**
     * 安装APK文件
     * @param context 上下文
     * @param filePath APK文件路径
     */
    fun installApk(context: Context, filePath: String): LaunchResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return LaunchResult.Failed(context.getString(R.string.update_apk_missing))
        }

        try {
            val uri = resolveInstallUri(context, file)
            val intent = buildInstallIntent(uri, file.name)

            if (intent.resolveActivity(context.packageManager) == null) {
                return openApkForManualInstall(context, filePath)
            }

            context.startActivity(intent)
            return LaunchResult.Launched
        } catch (e: Exception) {
            logE("启动APK安装失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            return openApkForManualInstall(context, filePath)
        }
    }

    fun openApkForManualInstall(context: Context, filePath: String): LaunchResult {
        val file = File(filePath)
        if (!file.exists() || !file.isFile) {
            return LaunchResult.Failed(context.getString(R.string.update_apk_missing))
        }

        return try {
            val uri = resolveInstallUri(context, file)
            val chooserIntent = buildManualInstallChooser(
                uri = uri,
                fileName = file.name,
                chooserTitle = context.getString(R.string.update_open_apk_chooser),
            )

            if (chooserIntent.resolveActivity(context.packageManager) == null) {
                LaunchResult.Failed(context.getString(R.string.update_no_apk_handler))
            } else {
                context.startActivity(chooserIntent)
                LaunchResult.ManualFallback(
                    context.getString(R.string.update_manual_install_opened),
                )
            }
        } catch (e: Exception) {
            logE("手动打开APK失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            LaunchResult.Failed(context.getString(R.string.update_open_apk_failed))
        }
    }

    /**
     * 检查是否有安装未知来源应用的权限
     * @param context 上下文
     * @return 是否有权限
     */
    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                context.packageManager.canRequestPackageInstalls()
            } catch (e: SecurityException) {
                logE("检查未知来源安装权限失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
                false
            }
        } else {
            true
        }
    }

    /**
     * 跳转到设置页面开启安装未知来源应用权限
     * @param context 上下文
     */
    fun requestInstallPermission(context: Context): LaunchResult {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return LaunchResult.Launched
        }

        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        return try {
            if (intent.resolveActivity(context.packageManager) == null) {
                LaunchResult.Failed(
                    context.getString(R.string.update_install_permission_settings_unavailable),
                )
            } else {
                context.startActivity(intent)
                LaunchResult.Launched
            }
        } catch (e: Exception) {
            logE("跳转未知来源安装权限页面失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            LaunchResult.Failed(
                context.getString(R.string.update_install_permission_settings_failed),
            )
        }
    }

    private fun resolveInstallUri(context: Context, sourceFile: File): Uri {
        return try {
            FileProviderHelper.getUriForFile(context, sourceFile)
        } catch (_: IllegalArgumentException) {
            val cacheDir = File(context.cacheDir, APK_INSTALL_CACHE_DIR)
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                throw IllegalStateException("无法创建安装缓存目录: ${cacheDir.absolutePath}")
            }

            val sharedApk = File(cacheDir, ensureApkSuffix(sourceFile.name))
            sourceFile.copyTo(sharedApk, overwrite = true)
            FileProviderHelper.getUriForFile(context, sharedApk)
        }
    }

    internal fun buildInstallIntent(uri: Uri, fileName: String): Intent = Intent(Intent.ACTION_VIEW).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(fileName, uri)
        setDataAndType(uri, APK_MIME_TYPE)
        putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
    }

    private fun buildManualInstallChooser(
        uri: Uri,
        fileName: String,
        chooserTitle: String,
    ): Intent {
        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(fileName, uri)
            setDataAndType(uri, APK_MIME_TYPE)
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(fileName, uri)
            putExtra(Intent.EXTRA_STREAM, uri)
            type = APK_MIME_TYPE
        }

        return Intent.createChooser(openIntent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(shareIntent))
        }
    }

    private fun ensureApkSuffix(fileName: String): String =
        if (fileName.endsWith(APK_SUFFIX, ignoreCase = true)) fileName else "$fileName$APK_SUFFIX"

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val APK_SUFFIX = ".apk"
    private const val APK_INSTALL_CACHE_DIR = "apk_install"
}
