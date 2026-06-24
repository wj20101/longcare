package com.ytone.longcare.features.nfctest.ui

import android.content.ClipData
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytone.longcare.common.utils.LockScreenOrientation
import com.ytone.longcare.common.utils.NfcUtils
import com.ytone.longcare.common.utils.showShortToast
import com.ytone.longcare.debug.NfcTestEntrySession
import com.ytone.longcare.features.nfctest.api.NfcTestActions
import com.ytone.longcare.features.nfctest.vm.NfcTestViewModel
import com.ytone.longcare.features.nfctest.vm.R65CHidInputTestViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcTestScreen(
    actions: NfcTestActions,
) {
    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

    val nfcTestViewModel: NfcTestViewModel = hiltViewModel()
    val r65cViewModel: R65CHidInputTestViewModel = hiltViewModel()
    val r65cPanelState by r65cViewModel.panelState.collectAsStateWithLifecycle()
    val testEntryEnabled by NfcTestEntrySession.enabled.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    val supportsNfc = remember(context) { NfcUtils.isNfcSupported(context) }

    val nfcTestHelper = if (testEntryEnabled && supportsNfc) {
        nfcTestViewModel.getHelper()
    } else {
        null
    }

    val lifecycleOwner = LocalLifecycleOwner.current

    if (testEntryEnabled && supportsNfc && nfcTestHelper != null) {
        BindNfcTestLifecycle(
            enabled = true,
            context = context,
            lifecycleOwner = lifecycleOwner,
            onEnable = nfcTestViewModel::enableNfcTest,
            onDisable = nfcTestViewModel::disableNfcTest,
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (testEntryEnabled && !supportsNfc) {
            R65CHidInputCaptureSurface(
                enabled = true,
                focusRequestToken = r65cPanelState.focusRequestToken,
                onFocusChanged = r65cViewModel::onFieldFocusChanged,
                onKeyCaptured = r65cViewModel::onCapturedKey,
            )
        }

        Scaffold(
            topBar = { NfcTestTopBar(onNavigateBack = actions.onNavigateBack) },
            containerColor = Color.Transparent,
        ) { paddingValues ->
            NfcTestBody(
                enabled = testEntryEnabled,
                supportsNfc = supportsNfc,
                r65cPanelState = r65cPanelState,
                onR65CRequestRefocus = r65cViewModel::requestRefocus,
                onR65CClearResult = r65cViewModel::clearLastResult,
                onR65CCopyResult = {
                    coroutineScope.launch {
                        copyNormalizedUidAndRefocus(
                            uid = r65cPanelState.lastNormalizedUid,
                            writeClipboardText = { text ->
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("NFC UID", text))
                                )
                            },
                            onCopied = { context.showShortToast("已复制卡号") },
                            onRequestRefocus = r65cViewModel::requestRefocus,
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        }
    }

    if (testEntryEnabled && supportsNfc && nfcTestHelper != null) {
        nfcTestHelper.NfcTagDialog()
    }
}

internal suspend fun copyNormalizedUidAndRefocus(
    uid: String?,
    writeClipboardText: suspend (String) -> Unit,
    onCopied: () -> Unit,
    onRequestRefocus: () -> Unit,
): Boolean {
    val normalizedUid = uid?.takeIf(String::isNotBlank) ?: return false

    writeClipboardText(normalizedUid)
    onCopied()
    onRequestRefocus()
    return true
}
