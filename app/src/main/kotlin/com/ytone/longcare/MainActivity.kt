package com.ytone.longcare

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ytone.longcare.common.event.AppEvent
import com.ytone.longcare.common.event.AppEventBus
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.navigation.MainApp
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    // 通过 Hilt 注入 EventBus
    @Inject
    lateinit var appEventBus: AppEventBus

    @Inject
    lateinit var toastHelper: ToastHelper

    // 获取 MainViewModel，它持有 UserSessionRepository
    private val viewModel: MainViewModel by viewModels()
    
    // 标记是否需要NFC前台调度（当在NFC签到页面时）
    private var needsNfcDispatch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 启动全局事件监听
        observeAppEvents()

        setContent {
            LongCareTheme {
                MainApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 如果需要NFC前台调度，则启用它
        if (needsNfcDispatch && NfcUtils.isNfcSupported(this) && NfcUtils.isNfcEnabled(this)) {
            NfcUtils.enableForegroundDispatch(this)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // 暂停时禁用NFC前台调度
        if (needsNfcDispatch) {
            NfcUtils.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 更新Activity的Intent，这样其他地方可以通过activity.intent获取到最新的Intent
        setIntent(intent)
        
        // 发送NFC Intent事件
        lifecycleScope.launch {
            appEventBus.sendEvent(AppEvent.NfcIntentReceived(intent))
        }
    }
    
    // 供外部调用，设置是否需要NFC前台调度
    fun setNfcDispatchNeeded(needed: Boolean) {
        needsNfcDispatch = needed
        if (needed && NfcUtils.isNfcSupported(this) && NfcUtils.isNfcEnabled(this)) {
            NfcUtils.enableForegroundDispatch(this)
        } else if (!needed) {
            NfcUtils.disableForegroundDispatch(this)
        }
    }

    private fun observeAppEvents() {
        lifecycleScope.launch {
            appEventBus.events.collect { event ->
                when (event) {
                    // 当收到 ForceLogout 事件时
                    is AppEvent.ForceLogout -> {
                        // 在主线程执行UI相关的登出操作
                        withContext(Dispatchers.Main) {
                            // 1. 显示提示信息
                            toastHelper.showLong("登录已失效，请重新登录")
                            // 2. 调用 ViewModel 执行登出业务逻辑
                            viewModel.forceLogout()
                            // 3. 导航逻辑已在 MainApp 中处理，当 sessionState 变为 LoggedOut 时会自动跳转
                        }
                    }
                    // 忽略NFC事件，由具体的Screen处理
                    is AppEvent.NfcIntentReceived -> {
                        // NFC事件由具体的Screen监听处理
                    }
                }
            }
        }
    }
}