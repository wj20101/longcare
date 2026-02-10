package com.ytone.longcare.common.utils

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

    /**
     * 安装APK文件
     * @param context 上下文
     * @param filePath APK文件路径
     */
    fun installApk(context: Context, filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            return
        }

        val intent = Intent(Intent.ACTION_VIEW)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        // minSdk=24，统一通过 FileProvider 暴露安装包 URI
        val uri: Uri = FileProviderHelper.getUriForFile(context, file)

        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            logE("启动APK安装失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
        }
    }

    /**
     * 检查是否有安装未知来源应用的权限
     * @param context 上下文
     * @return 是否有权限
     */
    fun canInstallApk(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * 跳转到设置页面开启安装未知来源应用权限
     * @param context 上下文
     */
    fun requestInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = "package:${context.packageName}".toUri()
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                logE("跳转未知来源安装权限页面失败: ${e.message}", tag = "ApkInstallUtils", throwable = e)
            }
        }
    }
}
