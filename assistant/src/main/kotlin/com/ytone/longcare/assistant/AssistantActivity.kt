package com.ytone.longcare.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ytone.longcare.common.utils.NfcIntentActions
import com.ytone.longcare.common.utils.NfcManager
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import com.ytone.longcare.theme.LongCareTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AssistantActivity : ComponentActivity() {
    @Inject lateinit var privacyConsent: PrivacyConsentManager
    @Inject lateinit var nfcManager: NfcManager
    @Inject lateinit var nfcTestHelper: NfcTestHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (privacyConsent.isPrivacyConsented) initializeAfterConsent()
        // Cold external intents never select routes or initiate verification.
        setContent { LongCareTheme { AssistantRoot(this, privacyConsent, nfcTestHelper) } }
    }

    fun initializeAfterConsent() {
        check(privacyConsent.isPrivacyConsented)
        (application as AssistantApplication).initializeAfterConsent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!privacyConsent.isPrivacyConsented || !NfcIntentActions.isSupportedTagAction(intent.action)) return
        // Only display a bounded tag; never follow URIs, nested intents or arbitrary route extras.
        val tag = try { NfcUtils.getTagFromIntent(intent) } catch (_: RuntimeException) { null }
        if (tag?.id?.size !in 1..64) return
        nfcManager.handleNfcIntent(this, Intent(intent.action).putExtra(android.nfc.NfcAdapter.EXTRA_TAG, tag))
    }

    override fun onDestroy() {
        nfcTestHelper.disable(this)
        super.onDestroy()
    }
}
