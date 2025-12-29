package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NFC管理器，负责管理NFC前台调度的生命周期
 * 使用方式：
 * 1. 在需要NFC功能的Screen中调用 enableNfcForActivity()
 * 2. 在Screen销毁时调用 disableNfcForActivity()
 */
@Singleton
class NfcManager @Inject constructor(
    private val appEventBus: AppEventBus
) : DefaultLifecycleObserver {
    
    private var currentActivity: Activity? = null
    private var isNfcEnabled = false
    private var nfcEnableDialog: AlertDialog? = null
    
    /**
     * 为指定Activity启用NFC功能
     * @param activity 需要启用NFC的Activity
     */
    fun enableNfcForActivity(activity: Activity) {
        logD("NfcManager", "enableNfcForActivity called for ${activity::class.java.simpleName}")
        currentActivity = activity
        isNfcEnabled = true
        
        // 添加生命周期观察者
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
            // 只有在Activity已经处于resumed状态时才立即启用NFC
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                logD("NfcManager", "Activity已resumed，立即检查并启用NFC")
                checkAndEnableNfc(activity)
            } else {
                logD("NfcManager", "Activity未 resumed，等待onResume时启用NFC")
            }
        } else {
            // 如果不是LifecycleOwner，直接尝试启用（兼容性处理）
            logD("NfcManager", "Activity不是LifecycleOwner，直接启用NFC")
            checkAndEnableNfc(activity)
        }
    }
    
    /**
     * 为指定Activity禁用NFC功能
     * @param activity 需要禁用NFC的Activity
     */
    fun disableNfcForActivity(activity: Activity) {
        logD("NfcManager", "disableNfcForActivity called for ${activity::class.java.simpleName}")
        logD("NfcManager", "  - currentActivity: ${currentActivity?.javaClass?.simpleName}")
        logD("NfcManager", "  - activity == currentActivity: ${activity == currentActivity}")
        
        if (currentActivity == activity) {
            logD("NfcManager", "禁用NFC功能")
            
            // 只设置isNfcEnabled为false，但不清空当前Activity的引用
            // 这样在onResume时可以重新启用
            isNfcEnabled = false
            
            // 检查Activity是否处于resumed状态，只有在resumed状态才能安全地禁用前台调度
            if (activity is LifecycleOwner && 
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                try {
                    NfcUtils.disableForegroundDispatch(activity)
                    logD("NfcManager", "NFC前台调度已禁用")
                } catch (e: IllegalStateException) {
                    logD("NfcManager", "禁用NFC前台调度失败: ${e.message}")
                }
            } else if (activity !is LifecycleOwner) {
                // 如果不是LifecycleOwner，直接尝试禁用（兼容性处理）
                try {
                    NfcUtils.disableForegroundDispatch(activity)
                    logD("NfcManager", "NFC前台调度已禁用")
                } catch (e: IllegalStateException) {
                    logD("NfcManager", "禁用NFC前台调度失败: ${e.message}")
                }
            }
            
            // 移除生命周期观察者
            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }
            
            // 只有在完全销毁时才清空currentActivity
            // currentActivity = null
        } else {
            logD("NfcManager", "currentActivity不匹配，跳过禁用NFC")
        }
    }
    
    /**
     * 处理NFC Intent，发送到事件总线
     * @param activity 接收Intent的Activity
     * @param intent NFC Intent
     */
    fun handleNfcIntent(activity: Activity, intent: Intent) {
        logD("NfcManager", "handleNfcIntent called")
        logD("NfcManager", "  - activity: ${activity::class.java.simpleName}")
        logD("NfcManager", "  - currentActivity: ${currentActivity?.javaClass?.simpleName}")
        logD("NfcManager", "  - isNfcEnabled: $isNfcEnabled")
        logD("NfcManager", "  - intent action: ${intent.action}")
        logD("NfcManager", "  - activity == currentActivity: ${activity == currentActivity}")
        
        if (currentActivity == activity && isNfcEnabled) {
            // 更新Activity的Intent
            activity.intent = intent
            
            // 发送NFC Intent事件
            if (activity is LifecycleOwner) {
                logD("NfcManager", "发送NFC Intent事件到AppEventBus")
                activity.lifecycleScope.launch {
                    appEventBus.send(AppEvent.NfcIntentReceived(intent))
                    logD("NfcManager", "NFC Intent事件已发送")
                }
            } else {
                logD("NfcManager", "Activity不是LifecycleOwner，无法发送事件")
            }
        } else {
            logD("NfcManager", "handleNfcIntent 条件不满足，忽略 Intent")
            if (currentActivity != activity) {
                logD("NfcManager", "  - currentActivity不等于activity")
            }
            if (!isNfcEnabled) {
                logD("NfcManager", "  - isNfcEnabled为false")
            }
        }
    }
    
    /**
     * 检查NFC状态并启用前台调度
     */
    private fun checkAndEnableNfc(activity: Activity) {
        logD("NfcManager", "checkAndEnableNfc called")
        // 检查Activity是否处于resumed状态
        if (activity is LifecycleOwner && 
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Activity未resumed状态，不能启用前台调度
            logD("NfcManager", "Activity未resumed状态，跳过启用NFC")
            return
        }
        
        when {
            !NfcUtils.isNfcSupported(activity) -> {
                // 设备不支持NFC，不做任何操作
                logD("NfcManager", "设备不支持NFC")
                dismissNfcDialog()
                return
            }
            !NfcUtils.isNfcEnabled(activity) -> {
                // NFC未开启，显示提示对话框（仅在对话框未显示时显示）
                if (nfcEnableDialog?.isShowing != true) {
                    logD("NfcManager", "NFC未开启，显示提示对话框")
                    nfcEnableDialog = NfcUtils.showEnableNfcDialog(
                        activity,
                        title = "NFC未开启",
                        message = "请在设置中开启NFC功能以使用签到功能"
                    )
                } else {
                    logD("NfcManager", "NFC未开启，对话框已显示")
                }
                return
            }
            else -> {
                // NFC已开启，关闭提示对话框并启用前台调度
                dismissNfcDialog()
                logD("NfcManager", "NFC已开启，启用前台调度")
                NfcUtils.enableForegroundDispatch(activity)
                logD("NfcManager", "NFC前台调度已启用")
            }
        }
    }
    
    /**
     * 关闭NFC提示对话框
     */
    private fun dismissNfcDialog() {
        nfcEnableDialog?.let { dialog ->
            if (dialog.isShowing) {
                logD("NfcManager", "关闭NFC提示对话框")
                dialog.dismiss()
            }
        }
        nfcEnableDialog = null
    }
    
    // LifecycleObserver 方法
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        logD("NfcManager", "onResume called")
        currentActivity?.let { activity ->
            logD("NfcManager", "onResume - 重新启用NFC")
            isNfcEnabled = true  // 重新启用NFC
            checkAndEnableNfc(activity)
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentActivity?.let { activity ->
            if (isNfcEnabled) {
                // 在onPause中安全地禁用前台调度
                try {
                    NfcUtils.disableForegroundDispatch(activity)
                } catch (e: IllegalStateException) {
                    // 忽略IllegalStateException，因为Activity可能已经不在resumed状态
                }
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        logD("NfcManager", "onDestroy called")
        // 关闭对话框，避免内存泄漏
        dismissNfcDialog()
        currentActivity?.let { activity ->
            logD("NfcManager", "在onDestroy中清理NFC状态")
            // 在销毁时彻底清理状态
            isNfcEnabled = false
            currentActivity = null
        }
    }
}