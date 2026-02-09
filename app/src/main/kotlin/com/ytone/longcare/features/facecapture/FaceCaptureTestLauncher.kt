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
    fun launch(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = "longcare://facecapture".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果通过URI启动失败，直接启动Activity
            val directIntent = Intent(context, FaceCaptureTestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(directIntent)
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
        
        return context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
    }
}
