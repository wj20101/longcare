package com.ytone.longcare.features.maindashboard.utils

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NFC测试助手类
 * 用于在首页测试NFC功能，便于后续删除
 * 
 * 功能：
 * 1. 当页面显示时自动开启NFC读取
 * 2. 读取到NFC标签时显示Toast并复制Tag ID到剪贴板
 * 3. 当页面不显示时自动关闭NFC读取
 * 4. 不影响其他页面的NFC功能
 */
@Singleton
class NfcTestHelper @Inject constructor(
    private val appEventBus: AppEventBus,
    private val toastHelper: ToastHelper
) : DefaultLifecycleObserver {
    
    private var currentActivity: Activity? = null
    private var isEnabled = false
    private var isListening = false
    
    // 弹窗状态管理
    var showDialog by mutableStateOf(false)
        private set
    var nfcTagId by mutableStateOf("")
        private set
    
    /**
     * 启用NFC测试功能
     * @param activity 当前Activity
     */
    fun enable(activity: Activity) {
        currentActivity = activity
        isEnabled = true
        
        // 添加生命周期观察者
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
            // 如果Activity已经处于resumed状态，立即启用NFC
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                startNfcListening(activity)
            }
        }
    }
    
    /**
     * 禁用NFC测试功能
     * @param activity 当前Activity
     */
    fun disable(activity: Activity) {
        if (currentActivity == activity) {
            isEnabled = false
            stopNfcListening(activity)
            
            // 移除生命周期观察者
            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }
            
            currentActivity = null
        }
    }
    
    /**
     * 开始NFC监听
     */
    private fun startNfcListening(activity: Activity) {
        if (!isEnabled || isListening) return
        
        // 检查NFC支持和状态
        when {
            !NfcUtils.isNfcSupported(activity) -> {
                toastHelper.showShort("设备不支持NFC功能")
                return
            }
            !NfcUtils.isNfcEnabled(activity) -> {
                toastHelper.showShort("请先开启NFC功能")
                return
            }
        }
        
        try {
            // 启用前台调度
            NfcUtils.enableForegroundDispatch(activity)
            isListening = true
            
            // 开始监听NFC事件
            if (activity is LifecycleOwner) {
                activity.lifecycleScope.launch {
                    appEventBus.events.collect { event ->
                        if (event is AppEvent.NfcIntentReceived && isListening) {
                            handleNfcIntent(event.intent)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            toastHelper.showShort("启动NFC监听失败: ${e.message}")
        }
    }
    
    /**
     * 停止NFC监听
     */
    private fun stopNfcListening(activity: Activity) {
        if (!isListening) return
        
        try {
            // 禁用前台调度
            NfcUtils.disableForegroundDispatch(activity)
            isListening = false
        } catch (e: Exception) {
            // 忽略异常，可能Activity已经不在resumed状态
        }
    }
    
    /**
     * 处理NFC Intent
     */
    private fun handleNfcIntent(intent: Intent) {
        try {
            // 获取Tag对象
            val tag = NfcUtils.getTagFromIntent(intent)
            if (tag != null) {
                // 获取Tag ID
                val tagId = NfcUtils.bytesToHexString(tag.id)
                
                // 显示弹窗
                nfcTagId = tagId
                showDialog = true
                
                // 发送统一的NFC事件（与业务NFC处理保持一致）
                currentActivity?.let { activity ->
                    if (activity is LifecycleOwner) {
                        activity.lifecycleScope.launch {
                            // 使用与NfcManager一致的事件类型
                            appEventBus.send(AppEvent.NfcIntentReceived(intent))
                        }
                    }
                }
            } else {
                toastHelper.showShort("无法读取NFC标签")
            }
        } catch (e: Exception) {
            toastHelper.showShort("处理NFC数据失败: ${e.message}")
        }
    }
    
    /**
     * 复制文本到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val activity = currentActivity ?: return
            val clipboardManager = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clipData = ClipData.newPlainText("NFC Tag ID", text)
            clipboardManager.setPrimaryClip(clipData)
            toastHelper.showShort("已复制到剪贴板")
        } catch (e: Exception) {
            // 忽略剪贴板操作异常
        }
    }
    
    /**
     * 关闭弹窗
     */
    fun dismissDialog() {
        showDialog = false
        nfcTagId = ""
    }
    
    /**
     * 复制并关闭弹窗
     */
    fun copyAndDismiss() {
        copyToClipboard(nfcTagId)
        dismissDialog()
    }
    
    /**
     * NFC标签检测弹窗
     */
    @Composable
    fun NfcTagDialog() {
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { dismissDialog() },
                title = {
                    Text(text = "检测到NFC标签")
                },
                text = {
                    Text(text = "Tag ID: $nfcTagId")
                },
                confirmButton = {
                    Button(
                        onClick = { copyAndDismiss() }
                    ) {
                        Text("复制")
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { dismissDialog() }
                    ) {
                        Text("关闭")
                    }
                }
            )
        }
    }
    
    // LifecycleObserver 方法
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentActivity?.let { activity ->
            if (isEnabled) {
                startNfcListening(activity)
            }
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentActivity?.let { activity ->
            if (isEnabled) {
                stopNfcListening(activity)
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        currentActivity?.let { activity ->
            disable(activity)
        }
    }
}