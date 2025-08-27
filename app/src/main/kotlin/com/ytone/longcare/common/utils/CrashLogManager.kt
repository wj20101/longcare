package com.ytone.longcare.common.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.pm.PackageInfoCompat

/**
 * 崩溃日志管理器
 * 负责崩溃日志的存储、检索和分享
 */
object CrashLogManager {
    
    private const val CRASH_LOG_DIR = "crash_logs"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
    
    /**
     * 保存崩溃日志到本地文件
     */
    suspend fun saveCrashLog(context: Context, throwable: Throwable): File? {
        return withContext(Dispatchers.IO) {
            try {
                val crashLogDir = File(context.filesDir, CRASH_LOG_DIR)
                if (!crashLogDir.exists()) {
                    crashLogDir.mkdirs()
                }
                
                val timestamp = dateFormat.format(Date())
                val fileName = "crash_$timestamp.log"
                val logFile = File(crashLogDir, fileName)
                
                val logContent = buildString {
                    appendLine("=== 崩溃日志 ===")
                    appendLine("时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                    appendLine("应用版本: ${getAppVersion(context)}")
                    appendLine("设备信息: ${getDeviceInfo()}")
                    appendLine("Android版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("制造商: ${Build.MANUFACTURER}")
                    appendLine("设备型号: ${Build.MODEL}")
                    appendLine("")
                    appendLine("=== 异常信息 ===")
                    appendLine("异常类型: ${throwable.javaClass.simpleName}")
                    appendLine("异常消息: ${throwable.message ?: "无消息"}")
                    appendLine("")
                    appendLine("=== 堆栈跟踪 ===")
                    append(getStackTrace(throwable))
                }
                
                logFile.writeText(logContent)
                logFile
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    
    /**
     * 获取所有崩溃日志文件
     * 按照文件修改时间倒序排列（最新的文件在前）
     */
    fun getAllCrashLogs(context: Context): List<File> {
        val crashLogDir = File(context.filesDir, CRASH_LOG_DIR)
        if (!crashLogDir.exists()) {
            return emptyList()
        }
        
        return crashLogDir.listFiles { file ->
            file.isFile && file.name.startsWith("crash_") && file.name.endsWith(".log")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
    
    /**
     * 分享崩溃日志文件
     */
    fun shareCrashLogs(context: Context, files: List<File>): Intent? {
        if (files.isEmpty()) return null
        
        return try {
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "崩溃日志报告")
                putExtra(Intent.EXTRA_TEXT, "请查看附件中的崩溃日志文件")
                
                val uris = files.map { file -> FileProviderHelper.getUriForFile(context,file) }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(intent, "分享崩溃日志")
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    

    
    /**
     * 获取应用版本信息
     */
    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${PackageInfoCompat.getLongVersionCode(packageInfo)})"
        } catch (_: Exception) {
            "未知版本"
        }
    }
    
    /**
     * 获取设备信息
     */
    private fun getDeviceInfo(): String {
        return "${Build.BRAND} ${Build.MODEL}"
    }
    
    /**
     * 获取异常堆栈跟踪
     */
    private fun getStackTrace(throwable: Throwable): String {
        val stringWriter = StringWriter()
        val printWriter = PrintWriter(stringWriter)
        throwable.printStackTrace(printWriter)
        return stringWriter.toString()
    }
    
    /**
     * 清理旧的崩溃日志文件（保留最近30天的日志）
     */
    fun cleanOldCrashLogs(context: Context) {
        val crashLogDir = File(context.filesDir, CRASH_LOG_DIR)
        if (!crashLogDir.exists()) return
        
        val thirtyDaysAgo = System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L)
        
        crashLogDir.listFiles { file ->
            file.isFile && file.lastModified() < thirtyDaysAgo
        }?.forEach { file ->
            file.delete()
        }
    }
}