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
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.navigation.MainApp
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
        
        // 【日志调试】检查启动Intent是否是NFC Intent - 与测试功能无关
        intent?.let {
            logD("MainActivity", "onCreate - intent action: ${it.action}")
            if (it.action?.startsWith("android.nfc.action") == true) {
                logD("MainActivity", "onCreate收到NFC Intent，延迟处理")
                handleNfcIntent(it, delayMillis = 500)
            }
        }

        setContent {
            LongCareTheme {
                MainApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        logD("MainActivity", "onNewIntent called with action: ${intent.action}")
        
        // 【业务功能】延迟处理NFC Intent，确保所有初始化完成 - 与测试功能无关
        if (intent.action?.startsWith("android.nfc.action") == true) {
            handleNfcIntent(intent, delayMillis = 100)
        } else {
            // 非NFC Intent直接处理
            nfcManager.handleNfcIntent(this, intent)
        }
    }

    private fun observeAppEvents() {
        lifecycleScope.launch {
            appEventBus.events.collect { event ->
                when (event) {
                    is AppEvent.ForceLogout -> {
                        withContext(Dispatchers.Main) {
                            toastHelper.showLong("登录已失效，请重新登录")
                            viewModel.forceLogout()
                        }
                    }

                    is AppEvent.NfcIntentReceived -> {
                        // 【业务功能】NFC事件由具体的Screen监听处理 - 与测试功能无关
                    }

                    is AppEvent.AppUpdate -> {
                        viewModel.setAppVersionModel(event.appVersionModel)
                    }
                }
            }
        }
    }

    private fun handleNfcIntent(intent: Intent, delayMillis: Long) {
        lifecycleScope.launch {
            delay(delayMillis)
            nfcManager.handleNfcIntent(this@MainActivity, intent)
        }
    }
}
