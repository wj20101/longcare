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
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import kotlinx.coroutines.Job
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
    private val toastHelper: ToastHelper,
    private val nfcManager: NfcManager
) : DefaultLifecycleObserver {
    
    private var currentActivity: Activity? = null
    private var isEnabled = false
    private var isListening = false
    private var eventJob: Job? = null
    
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
        logD("NfcTestHelper", "启用NFC测试功能")
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
            
            // 清理状态
            dismissDialog()
            currentActivity = null
        }
    }
    
    /**
     * 开始NFC监听
     */
    private fun startNfcListening(activity: Activity) {
        logD("NfcTestHelper", "开始NFC监听 - isEnabled: $isEnabled, isListening: $isListening")
        if (!isEnabled || isListening) {
            logD("NfcTestHelper", "跳过NFC监听 - isEnabled: $isEnabled, isListening: $isListening")
            return
        }
        
        // 检查NFC支持和状态
        when {
            !NfcUtils.isNfcSupported(activity) -> {
                logD("NfcTestHelper", "设备不支持NFC")
                toastHelper.showShort("设备不支持NFC功能")
                return
            }
            !NfcUtils.isNfcEnabled(activity) -> {
                logD("NfcTestHelper", "NFC未开启")
                toastHelper.showShort("请先开启NFC功能")
                return
            }
        }
        
        try {
            // 使用NfcManager启用NFC前台调度
            logD("NfcTestHelper", "通过NfcManager启用NFC功能")
            nfcManager.enableNfcForActivity(activity)
            isListening = true
            toastHelper.showShort("NFC测试模式已启用，请靠近NFC标签")
            
            // 取消之前的事件监听
            eventJob?.cancel()
            
            // 开始监听NFC事件
            if (activity is LifecycleOwner) {
                logD("NfcTestHelper", "开始监听AppEventBus事件")
                eventJob = activity.lifecycleScope.launch {
                    logD("NfcTestHelper", "事件监听协程已启动")
                    appEventBus.events.collect { event ->
                        logD("NfcTestHelper", "收到事件: ${event::class.java.simpleName}, isListening: $isListening")
                        if (event is AppEvent.NfcIntentReceived && isListening) {
                            logD("NfcTestHelper", "处理NFC事件: ${event.intent.action}")
                            handleNfcIntent(event.intent)
                        } else if (event is AppEvent.NfcIntentReceived) {
                            logD("NfcTestHelper", "收到NFC事件但isListening为false")
                        }
                    }
                }
                logD("NfcTestHelper", "事件监听协程已设置完成")
            } else {
                logD("NfcTestHelper", "Activity不是LifecycleOwner，无法监听事件")
            }
        } catch (e: Exception) {
            logD("NfcTestHelper", "启动NFC监听失败: ${e.message}")
            toastHelper.showShort("启动NFC监听失败: ${e.message}")
        }
    }
    
    /**
     * 停止NFC监听
     */
    private fun stopNfcListening(activity: Activity) {
        if (!isListening) return
        
        try {
            // 取消事件监听
            eventJob?.cancel()
            eventJob = null
            
            // 通过NfcManager禁用NFC
            logD("NfcTestHelper", "通过NfcManager禁用NFC功能")
            nfcManager.disableNfcForActivity(activity)
            isListening = false
            logD("NfcTestHelper", "NFC监听已停止")
        } catch (e: Exception) {
            // 忽略异常，可能Activity已经不在resumed状态
            logD("NfcTestHelper", "停止NFC监听异常: ${e.message}")
        }
    }
    
    /**
     * 处理NFC Intent
     */
    private fun handleNfcIntent(intent: Intent) {
        try {
            logD("NfcTestHelper", "处理NFC Intent: ${intent.action}")
            
            // 获取Tag对象
            val tag = NfcUtils.getTagFromIntent(intent)
            if (tag != null) {
                // 获取Tag ID
                val tagId = NfcUtils.bytesToHexString(tag.id)
                logD("NfcTestHelper", "获取到Tag ID: $tagId")
                
                if (tagId.isNotEmpty()) {
                    // 显示弹窗
                    nfcTagId = tagId
                    showDialog = true
                    logD("NfcTestHelper", "已设置弹窗显示: showDialog=$showDialog, nfcTagId=$nfcTagId")
                } else {
                    toastHelper.showShort("无法获取NFC标签ID")
                }
            } else {
                logD("NfcTestHelper", "无法从 Intent 中获取 Tag")
                toastHelper.showShort("无法读取NFC标签")
            }
        } catch (e: Exception) {
            logD("NfcTestHelper", "处理NFC数据失败: ${e.message}")
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
            logD("NfcTestHelper", "已复制到剪贴板: $text")
            toastHelper.showShort("已复制到剪贴板")
        } catch (e: Exception) {
            logD("NfcTestHelper", "复制到剪贴板失败: ${e.message}")
            toastHelper.showShort("复制失败")
        }
    }
    
    /**
     * 关闭弹窗
     */
    fun dismissDialog() {
        logD("NfcTestHelper", "关闭弹窗")
        showDialog = false
        nfcTagId = ""
    }
    
    /**
     * 复制并关闭弹窗
     */
    fun copyAndDismiss() {
        logD("NfcTestHelper", "复制Tag ID: $nfcTagId")
        copyToClipboard(nfcTagId)
        dismissDialog()
    }
    
    /**
     * NFC标签检测弹窗
     */
    @Composable
    fun NfcTagDialog() {
        if (showDialog) {
            logD("NfcTestHelper", "显示NFC弹窗 - nfcTagId: $nfcTagId")
            AlertDialog(
                onDismissRequest = {
                    // 点击空白处不可取消，所以这里不做任何操作
                },
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
        logD("NfcTestHelper", "onResume - isEnabled: $isEnabled, isListening: $isListening")
        currentActivity?.let { activity ->
            if (isEnabled && !isListening) {
                startNfcListening(activity)
            }
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        logD("NfcTestHelper", "onPause - isListening: $isListening")
        currentActivity?.let { activity ->
            if (isListening) {
                stopNfcListening(activity)
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        logD("NfcTestHelper", "onDestroy")
        currentActivity?.let { activity ->
            disable(activity)
        }
    }
}