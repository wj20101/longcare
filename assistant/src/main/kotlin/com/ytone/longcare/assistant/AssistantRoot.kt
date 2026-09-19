package com.ytone.longcare.assistant

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.features.face.ui.ManualFaceCaptureScreen
import com.ytone.longcare.features.identification.facecheck.DefaultFaceVerificationScreen
import com.ytone.longcare.features.maindashboard.utils.NfcTestHelper
import com.ytone.longcare.features.photoupload.api.CameraActions
import com.ytone.longcare.features.photoupload.ui.CameraScreen
import com.ytone.longcare.features.shared.FaceVerificationWithAutoSignScreen
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.presentation.validation.nfc.NfcValidationScreen
import java.io.File

@Composable
fun AssistantRoot(activity: AssistantActivity, privacy: PrivacyConsentManager, nfc: NfcTestHelper) {
    var consented by remember { mutableStateOf(privacy.isPrivacyConsented) }
    if (!consented) {
        AssistantPrivacyScreen(
            onAccept = { privacy.markConsented(); activity.initializeAfterConsent(); consented = true },
            onReject = activity::finish,
        )
        return
    }
    // Do not construct network-backed ViewModels before privacy consent.
    AssistantNavigation(activity, nfc)
}

@Composable
internal fun AssistantNavigation(
    activity: AssistantActivity,
    nfc: NfcTestHelper,
    viewModel: AssistantSessionViewModel = hiltViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val invalidation by viewModel.invalidation.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()
    val photo by viewModel.photoUri.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    if (session is SessionState.Unknown) {
        AssistantPage(stringResource(R.string.app_name)) { CircularProgressIndicator() }
        return
    }
    val stack = rememberNavBackStack(AssistantEntry(AssistantHome))
    val navigator = remember(stack) { AssistantNavigator(stack) }
    val identity = session.user?.userId?.toString() ?: "anonymous"
    var savedIdentity by rememberSaveable { mutableStateOf(identity) }
    if (savedIdentity != identity) {
        if (savedIdentity != "anonymous") {
            val pending = (stack.lastOrNull() as? AssistantEntry)?.route as? AssistantToolRoute
            navigator.reset()
            viewModel.clearResult()
            viewModel.cancelPending()
            if (identity == "anonymous" && pending?.tool?.requiresLogin == true) {
                viewModel.requireLogin(pending)
                navigator.login()
            }
        }
        savedIdentity = identity
    }
    LaunchedEffect(invalidation?.id) {
        invalidation?.let {
            viewModel.clearResult()
            viewModel.report(resources.getString(R.string.assistant_session_expired))
            viewModel.consumeInvalidation(it.id)
        }
    }
    NavDisplay(
        backStack = stack,
        onBack = {
            if ((stack.lastOrNull() as? AssistantEntry)?.route == AssistantLogin) viewModel.cancelPending()
            navigator.back()
        },
        entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
        entryProvider = entryProvider {
            entry<AssistantEntry>(clazzContentKey = { it.id }) { entry ->
                val nav = navigator.forEntry(entry.id)
                val back: () -> Unit = nav::back
                val home: () -> Unit = nav::home
                val login: (AssistantToolRoute) -> Unit = { route -> viewModel.requireLogin(route); nav.login() }
                when (val route = entry.route) {
                    AssistantHome -> {
                        AssistantHomeScreen(
                            loggedIn = session is SessionState.LoggedIn,
                            result = result,
                            photoUri = photo,
                            onClear = viewModel::clearResult,
                            onLogout = viewModel::logout,
                            onLogin = { viewModel.cancelPending(); nav.navigate(AssistantLogin) },
                            onOpen = { tool ->
                                viewModel.clearResult()
                                val route = AssistantToolRoute(tool)
                                if (tool.requiresLogin && session !is SessionState.LoggedIn) login(route)
                                else nav.navigate(route)
                            },
                        )
                    }
                    AssistantLogin -> {
                        AssistantLoginScreen(onBack = { viewModel.cancelPending(); home() })
                        LaunchedEffect(session) {
                            if (session is SessionState.LoggedIn) {
                                val pending = viewModel.takePending()
                                nav.resume(pending)
                            }
                        }
                    }
                    is AssistantToolRoute -> {
                        if (route.tool.requiresLogin && session !is SessionState.LoggedIn) {
                            LaunchedEffect(route) { login(route) }
                            return@entry
                        }
                        when (route.tool) {
                            AssistantTool.NFC -> NfcValidationScreen(
                                activity = activity, nfcTestHelper = nfc, onNavigateBack = back,
                                onOpenNfcSettings = { activity.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                            )
                            AssistantTool.CAMERA -> CameraScreen(
                                actions = CameraActions { uri -> nav.ifCurrent { viewModel.showPhoto(uri); home() } },
                                watermarkData = WatermarkData(
                                    title = stringResource(R.string.assistant_camera_watermark),
                                    insuredPerson = "", caregiver = "", address = "",
                                ),
                            )
                            AssistantTool.MANUAL_FACE -> ManualFaceCaptureScreen(
                                onNavigateBack = back,
                                onFaceCaptured = { path -> nav.ifCurrent { viewModel.showPhoto(Uri.fromFile(File(path)).toString()); home() } },
                            )
                            AssistantTool.TENCENT_FACE -> FaceVerificationWithAutoSignScreen(
                                currentUserId = session.user?.userId?.toString(),
                                onNavigateBack = back,
                                onVerificationSuccess = {
                                    nav.ifCurrent {
                                        completeAssistantFaceVerification(
                                            message = resources.getString(R.string.assistant_face_success),
                                            report = viewModel::report,
                                            returnHome = home,
                                        )
                                    }
                                },
                            )
                            AssistantTool.DEFAULT_FACE -> {
                                if (route.orderId == 0L) {
                                    AssistantOrderScreen(onBack = back, onStart = { orderId ->
                                            viewModel.clearResult()
                                            nav.navigate(AssistantToolRoute(AssistantTool.DEFAULT_FACE, orderId))
                                    })
                                } else {
                                    AssistantDefaultFaceScreen(
                                        orderId = route.orderId,
                                        onNavigateBack = home,
                                        onPhotoPrepared = { metrics ->
                                            nav.ifCurrent {
                                                viewModel.report(resources.getString(
                                                        R.string.assistant_face_metrics, metrics.widthPx, metrics.heightPx, metrics.byteCount,
                                                ))
                                            }
                                        },
                                        onOutcome = { outcome ->
                                            nav.ifCurrent { viewModel.report(resources.getString(outcome.messageRes) + "\n" + viewModel.result.value) }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
    )
}

internal fun completeAssistantFaceVerification(
    message: String,
    report: (String) -> Unit,
    returnHome: () -> Unit,
) {
    report(message)
    returnHome()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AssistantPage(title: String, onBack: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
            TopAppBar(title = { Text(title) }, navigationIcon = {
                    if (onBack != null) TextButton(onClick = onBack) { Text(stringResource(R.string.assistant_back)) }
            })
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), content = content,
        )
    }
}

@Composable
internal fun AssistantOrderScreen(onBack: () -> Unit, onStart: (Long) -> Unit) {
    var order by rememberSaveable { mutableStateOf("") }
    var edited by rememberSaveable { mutableStateOf(false) }
    val id = validAssistantOrderId(order)
    AssistantPage(stringResource(R.string.assistant_default_face), onBack) {
        Text(stringResource(R.string.assistant_real_api_notice))
        OutlinedTextField(value = order, onValueChange = { order = it; edited = true },
            label = { Text(stringResource(R.string.assistant_order_id)) }, singleLine = true,
            isError = edited && id == null,
            supportingText = {
                if (edited && id == null) Text(stringResource(R.string.assistant_invalid_order_id))
        })
        Button(onClick = { id?.let(onStart) }, enabled = id != null) {
            Text(stringResource(R.string.assistant_start))
        }
    }
}

internal fun validAssistantOrderId(value: String): Long? =
value.toLongOrNull()?.takeIf { it in 1L..Int.MAX_VALUE.toLong() }
