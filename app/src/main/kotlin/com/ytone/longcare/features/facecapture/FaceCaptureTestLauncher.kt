package com.ytone.longcare.features.facecapture

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * 人脸捕获功能测试启动器
 * 提供便捷的方式来启动人脸捕获测试
 */
object FaceCaptureTestLauncher {
    
    /**
     * 启动人脸捕获测试Activity
     * @param context 上下文
     */
    fun launch(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "longcare://facecapture".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return true
        }

        val directIntent = Intent(context, FaceCaptureTestActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return if (directIntent.resolveActivity(context.packageManager) != null) {
            context.startActivity(directIntent)
            true
        } else {
            false
        }
    }
    
    /**
     * 检查是否可以启动人脸捕获功能
     * @param context 上下文
     * @return 是否可以启动
     */
    fun canLaunch(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "longcare://facecapture".toUri()
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            return true
        }

        val directIntent = Intent(context, FaceCaptureTestActivity::class.java)
        return directIntent.resolveActivity(context.packageManager) != null
    }
}
