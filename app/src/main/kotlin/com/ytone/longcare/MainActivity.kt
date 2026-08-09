package com.ytone.longcare

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.ytone.longcare.common.network.SessionInvalidationHandler
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.ToastHelper
import com.ytone.longcare.common.utils.logD
import com.ytone.longcare.navigation.MainApp
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var toastHelper: ToastHelper
    
    @Inject
    lateinit var nfcManager: NfcManager

    @Inject
    lateinit var sessionInvalidationHandler: SessionInvalidationHandler

    // 获取 MainViewModel，它持有 UserSessionRepository
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        observeSessionInvalidations()
        
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

    private fun observeSessionInvalidations() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                sessionInvalidationHandler.invalidations
                    .filterNotNull()
                    .collect { invalidation ->
                        toastHelper.showLong("登录已失效，请重新登录")
                        sessionInvalidationHandler.consume(invalidation.id)
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
