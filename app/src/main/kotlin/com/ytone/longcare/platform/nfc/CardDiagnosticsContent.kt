package com.ytone.longcare.platform.nfc

import android.nfc.NfcAdapter
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.currentStateAsState
import com.ytone.longcare.feature.carddiagnostics.CardDiagnosticsMode
import com.ytone.longcare.feature.carddiagnostics.CardDiagnosticsScreen

/** Reader mode delivers tags only to this page, never to the business event bus. */
@Composable
internal fun CardDiagnosticsContent(onBack: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val adapter = remember(activity) { NfcAdapter.getDefaultAdapter(activity) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycle by lifecycleOwner.lifecycle.currentStateAsState()
    var mode by rememberSaveable { mutableStateOf(
        if (adapter == null) CardDiagnosticsMode.R65C else CardDiagnosticsMode.NFC
    ) }
    var enabled by remember { mutableStateOf(adapter?.isEnabled == true) }
    var tag by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(mode, adapter) {
        enabled = adapter?.isEnabled == true
        val reader = adapter?.takeIf { enabled && mode == CardDiagnosticsMode.NFC }
            ?.let { CardDiagnosticsReader(activity, it) { value -> tag = value.orEmpty() } }
        reader?.start()
        onPauseOrDispose { reader?.stop() }
    }
    CardDiagnosticsScreen(
        mode = mode,
        active = lifecycle == Lifecycle.State.RESUMED,
        nfcSupported = adapter != null,
        nfcEnabled = enabled,
        nfcTag = tag,
        onModeChange = { mode = it },
        onClearNfc = { tag = null },
        onOpenNfcSettings = { activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
        onBack = onBack,
    )
}
