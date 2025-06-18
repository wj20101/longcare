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
import com.ytone.longcare.common.utils.NfcManager
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
    
    @Inject
    lateinit var nfcManager: NfcManager

    // 获取 MainViewModel，它持有 UserSessionRepository
    private val viewModel: MainViewModel by viewModels()

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // 委托给NfcManager处理NFC Intent
        nfcManager.handleNfcIntent(this, intent)
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