package com.ytone.longcare.common.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import java.io.File

/**
 * APK安装工具类
 */
object ApkInstallUtils {

    sealed interface LaunchResult {
        data object Launched : LaunchResult
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
            return LaunchResult.Failed("安装包不存在")
        }

        try {
            val uri = resolveInstallUri(context, file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(file.name, uri)
                setDataAndType(uri, APK_MIME_TYPE)
            }

            if (intent.resolveActivity(context.packageManager) == null) {
                return LaunchResult.Failed("未找到可用的安装程序")
            }

            context.startActivity(intent)
            return LaunchResult.Launched
        } catch (e: Exception) {
            logE("启动APK安装失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            return LaunchResult.Failed("启动安装失败: ${e.message ?: "未知错误"}")
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
                LaunchResult.Failed("无法打开安装未知来源应用权限设置页")
            } else {
                context.startActivity(intent)
                LaunchResult.Launched
            }
        } catch (e: Exception) {
            logE("跳转未知来源安装权限页面失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            LaunchResult.Failed("打开安装权限设置失败: ${e.message ?: "未知错误"}")
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

    private fun ensureApkSuffix(fileName: String): String =
        if (fileName.endsWith(APK_SUFFIX, ignoreCase = true)) fileName else "$fileName$APK_SUFFIX"

    private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val APK_SUFFIX = ".apk"
    private const val APK_INSTALL_CACHE_DIR = "apk_install"
}
