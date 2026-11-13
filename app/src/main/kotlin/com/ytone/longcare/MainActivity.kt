package com.ytone.longcare

import android.content.Intent
import com.ytone.longcare.features.location.manager.LocationTrackingManager
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
import com.ytone.longcare.navigation.MainApp
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
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
        
        // An NFC launch without an attached order is consumed, never retained for a later page.
        intent?.let { nfcManager.handleNfcIntent(this, it, dispatchAllowed = savedInstanceState == null) }

        setContent {
            LongCareTheme {
                MainApp(viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        locationTrackingManager.onAppVisible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcManager.handleNfcIntent(this, intent)
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

}
