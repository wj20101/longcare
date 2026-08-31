package com.ytone.longcare.presentation.validation

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.debug.NfcTestEntrySession
import com.ytone.longcare.presentation.validation.nfc.NfcTestHelper
import com.ytone.longcare.presentation.validation.nfc.NfcValidationScreen
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Internal capability verification for native NFC and R65C external readers. */
@AndroidEntryPoint
class NfcValidationActivity : ComponentActivity() {

    @Inject
    lateinit var nfcTestHelper: NfcTestHelper

    @Inject
    lateinit var nfcManager: NfcManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        NfcTestEntrySession.setEnabled(true)

        setContent {
            LongCareTheme {
                NfcValidationScreen(
                    activity = this,
                    nfcTestHelper = nfcTestHelper,
                    onNavigateBack = ::finish,
                    onOpenNfcSettings = ::openNfcSettings,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        nfcManager.handleNfcIntent(this, intent)
    }

    override fun onDestroy() {
        nfcTestHelper.disable(this)
        NfcTestEntrySession.setEnabled(false)
        super.onDestroy()
    }

    private fun openNfcSettings() {
        val nfcSettings = Intent(Settings.ACTION_NFC_SETTINGS)
        val fallbackSettings = Intent(Settings.ACTION_WIRELESS_SETTINGS)
        runCatching { startActivity(nfcSettings) }
            .onFailure { startActivity(fallbackSettings) }
    }
}
