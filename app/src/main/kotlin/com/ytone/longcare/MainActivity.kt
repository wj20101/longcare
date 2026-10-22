package com.ytone.longcare

import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.features.location.manager.LocationTrackingManager
import com.ytone.longcare.features.location.reporting.LocationReportingStatus
import com.ytone.longcare.common.utils.UnifiedPermissionHelper
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

    @Inject lateinit var locationTrackingManager: LocationTrackingManager

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
                val pendingRecovery by locationTrackingManager.pendingRecovery.collectAsStateWithLifecycle()
                val locationStatus by locationTrackingManager.status.collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize()) {
                    if (pendingRecovery != null || locationStatus == LocationReportingStatus.INTERRUPTED) {
                        Surface(Modifier.fillMaxWidth().statusBarsPadding()) {
                            TextButton(onClick = {
                                when {
                                    !UnifiedPermissionHelper.hasLocationPermission(this@MainActivity) ->
                                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                            "package:$packageName".toUri()))
                                    !UnifiedPermissionHelper.isLocationServiceEnabled(this@MainActivity) ->
                                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                    else -> {
                                        locationTrackingManager.resumePendingRecovery()
                                        locationTrackingManager.onAppVisible()
                                    }
                                }
                            }) { Text(getString(R.string.location_recovery_action)) }
                        }
                    }
                    Box(Modifier.weight(1f)) { MainApp(viewModel) }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        locationTrackingManager.onAppVisible()
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
                        if (sessionInvalidationHandler.consume(invalidation.id)) {
                            toastHelper.showLong(R.string.session_expired)
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
