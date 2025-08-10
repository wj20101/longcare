package com.ytone.longcare.common.utils

import android.app.Activity
import android.content.Intent
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
    
    /**
     * 为指定Activity启用NFC功能
     * @param activity 需要启用NFC的Activity
     */
    fun enableNfcForActivity(activity: Activity) {
        currentActivity = activity
        isNfcEnabled = true
        
        // 添加生命周期观察者
        if (activity is LifecycleOwner) {
            activity.lifecycle.addObserver(this)
            // 只有在Activity已经处于resumed状态时才立即启用NFC
            if (activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                checkAndEnableNfc(activity)
            }
        } else {
            // 如果不是LifecycleOwner，直接尝试启用（兼容性处理）
            checkAndEnableNfc(activity)
        }
    }
    
    /**
     * 为指定Activity禁用NFC功能
     * @param activity 需要禁用NFC的Activity
     */
    fun disableNfcForActivity(activity: Activity) {
        if (currentActivity == activity) {
            isNfcEnabled = false
            NfcUtils.disableForegroundDispatch(activity)
            
            // 移除生命周期观察者
            if (activity is LifecycleOwner) {
                activity.lifecycle.removeObserver(this)
            }
            
            currentActivity = null
        }
    }
    
    /**
     * 处理NFC Intent，发送到事件总线
     * @param activity 接收Intent的Activity
     * @param intent NFC Intent
     */
    fun handleNfcIntent(activity: Activity, intent: Intent) {
        if (currentActivity == activity && isNfcEnabled) {
            // 更新Activity的Intent
            activity.intent = intent
            
            // 发送NFC Intent事件
            if (activity is LifecycleOwner) {
                activity.lifecycleScope.launch {
                    appEventBus.send(AppEvent.NfcIntentReceived(intent))
                }
            }
        }
    }
    
    /**
     * 检查NFC状态并启用前台调度
     */
    private fun checkAndEnableNfc(activity: Activity) {
        // 检查Activity是否处于resumed状态
        if (activity is LifecycleOwner && 
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            // Activity未处于resumed状态，不能启用前台调度
            return
        }
        
        when {
            !NfcUtils.isNfcSupported(activity) -> {
                // 设备不支持NFC，不做任何操作
                return
            }
            !NfcUtils.isNfcEnabled(activity) -> {
                // NFC未开启，显示提示对话框
                NfcUtils.showEnableNfcDialog(
                    activity,
                    title = "NFC未开启",
                    message = "请在设置中开启NFC功能以使用签到功能"
                )
                return
            }
            else -> {
                // NFC已开启，启用前台调度
                NfcUtils.enableForegroundDispatch(activity)
            }
        }
    }
    
    // LifecycleObserver 方法
    override fun onResume(owner: LifecycleOwner) {
        super.onResume(owner)
        currentActivity?.let { activity ->
            if (isNfcEnabled) {
                checkAndEnableNfc(activity)
            }
        }
    }
    
    override fun onPause(owner: LifecycleOwner) {
        super.onPause(owner)
        currentActivity?.let { activity ->
            if (isNfcEnabled) {
                NfcUtils.disableForegroundDispatch(activity)
            }
        }
    }
    
    override fun onDestroy(owner: LifecycleOwner) {
        super.onDestroy(owner)
        currentActivity?.let { activity ->
            disableNfcForActivity(activity)
        }
    }
}