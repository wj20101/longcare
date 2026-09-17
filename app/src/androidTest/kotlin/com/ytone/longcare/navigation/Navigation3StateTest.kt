package com.ytone.longcare.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.BackEventCompat
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.presentation.sales.rememberSalesNavigationState
import com.ytone.longcare.presentation.sales.SalesPage
import androidx.activity.compose.BackHandler
import com.ytone.longcare.shared.vm.TodayOrderViewModel
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.MainViewModel
import com.ytone.longcare.common.utils.DeviceUtils
import com.ytone.longcare.common.utils.PrivacyConsentManager
import com.ytone.longcare.domain.repository.SessionState
import com.ytone.longcare.domain.repository.UserSessionRepository
import com.ytone.longcare.model.User
import com.ytone.longcare.worker.StartupUpdateWorkObserver
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.After

/** Real NavDisplay, entry decorator, state registry and navigator; only page content is fake. */
class Navigation3StateTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationStateTestActivity>()
    @After fun clearRecreationContent() { NavigationStateTestActivity.recreatedContent = null }
    private lateinit var navigator: AppNavigator
    private val shared = mutableMapOf<AppRoute, ProbeViewModel>()
    private val todayShared = mutableMapOf<AppRoute, TodayOrderViewModel>()
    private val individual = mutableMapOf<String, ProbeViewModel>()
    private val identity = mutableStateOf("user-1")
    private lateinit var activity: ComponentActivity
    private val camera = CameraRoute(WatermarkData("测试", "", "", ""))
    private val photoMap = mapOf(ImageTaskType.BEFORE_CARE to listOf(ImageTask("1", "content://photo", ImageTaskType.BEFORE_CARE)))

    class ProbeViewModel : ViewModel() {
        var cleared = false
        override fun onCleared() { cleared = true }
    }

    @Composable private fun Content() {
        val context = LocalContext.current
        SideEffect {
            var owner = context
            while (owner is android.content.ContextWrapper && owner !is ComponentActivity) owner = owner.baseContext
            activity = owner as ComponentActivity
        }
        AppNavigationHost(if (identity.value == "anonymous") LoginRoute else HomeRoute, identity.value) { nav ->
            navigator = nav
            AppEntryProviderBuilder().apply {
                destination<HomeRoute> { HomeContent(it, nav) }
                destination<CarePlansListRoute> { ListContent(it, nav) }
                destination<ServiceRecordsListRoute> { ListContent(it, nav) }
                destination<WebViewRoute> { ListContent(it, nav) }
                destination<LoginRoute> { Text("LOGIN") }
                destination<CameraRoute> { entry ->
                    val page = nav.forEntry(entry.id)
                    val own: ProbeViewModel = viewModel()
                    SideEffect { individual[entry.id] = own }
                    Button(onClick = {
                        page.returnResult(NavigationConstants.CAPTURED_IMAGE_URI_KEY, "content://same")
                    }) { Text("CAPTURE") }
                }
                destination<ManualFaceCaptureRoute> { entry ->
                    Button(onClick = { nav.forEntry(entry.id).returnResult(NavigationConstants.FACE_IMAGE_PATH_KEY, "/mock/face.jpg") }) { Text("MANUAL SUCCESS") }
                }
                destination<DefaultFaceVerificationRoute> { entry ->
                    Column {
                        Button(onClick = { nav.forEntry(entry.id).returnResult(NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY, true) }) { Text("VERIFY SUCCESS") }
                        Button(onClick = { nav.forEntry(entry.id).returnResult(NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY, false) }) { Text("VERIFY CANCEL") }
                    }
                }
                destination<PhotoUploadRoute> { entry ->
                    val page = nav.forEntry(entry.id)
                    val inputFlow = remember(entry.id) {
                        page.previousBackStackEntry?.results?.getStateFlow<Map<ImageTaskType, List<ImageTask>>>(NavigationConstants.EXISTING_IMAGES_KEY, null)
                            ?: kotlinx.coroutines.flow.MutableStateFlow(null)
                    }
                    val input by inputFlow.collectAsState()
                    val captured by entry.results.getStateFlow<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY, null).collectAsState()
                    Column {
                        Text("INPUT ${input?.size}")
                        Text(captured ?: "NO CAPTURE")
                        Button(onClick = { page.navigate(camera) }) { Text("NESTED CAMERA") }
                        Button(onClick = { page.returnResult(NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, photoMap) }) { Text("PUBLISH") }
                    }
                }
            }
        }
    }

    @Composable private fun HomeContent(entry: AppEntryHandle, nav: AppNavigator) {
        val vm = sharedModel(HomeRoute)
        SideEffect { shared[HomeRoute] = vm }
        val uri by entry.results.getStateFlow<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY, null).collectAsState()
        val face by entry.results.getStateFlow<String>(NavigationConstants.FACE_IMAGE_PATH_KEY, null).collectAsState()
        val verified by entry.results.getStateFlow<Boolean>(NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY, null).collectAsState()
        val photos by entry.results.getStateFlow<Map<ImageTaskType, List<ImageTask>>>(NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, null).collectAsState()
        val sales = rememberSalesNavigationState()
        BackHandler(enabled = sales.canHandleBack) { sales.goHome() }
        val page = nav.forEntry(entry.id)
        Column {
            Text("HOME")
            Text(uri ?: "EMPTY")
            Text("SALES ${sales.currentPage}")
            Text("FACE $face / VERIFIED $verified / PHOTOS ${photos?.size}")
            Button(onClick = { sales.navigate(SalesPage.REGISTRATION) }) { Text("OPEN SALES REGISTRATION") }
            Button(onClick = { page.navigateToCarePlansList() }) { Text("PLANS") }
            Button(onClick = { page.navigateToServiceRecordsList() }) { Text("RECORDS") }
            Button(onClick = { page.navigate(camera) }) { Text("CAMERA") }
            Button(onClick = { page.navigate(ManualFaceCaptureRoute) }) { Text("MANUAL") }
            Button(onClick = { page.navigate(DefaultFaceVerificationRoute(OrderNavParams(1))) }) { Text("VERIFY") }
            Button(onClick = {
                entry.results.set(NavigationConstants.EXISTING_IMAGES_KEY, photoMap)
                page.navigate(PhotoUploadRoute(OrderNavParams(1)))
            }) { Text("UPLOAD") }
            Button(onClick = { entry.results.remove<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY) }) { Text("CONSUME") }
        }
    }

    @Composable private fun ListContent(entry: AppEntryHandle, nav: AppNavigator) {
        val vm = sharedModel(entry.entry.route)
        val own: ProbeViewModel = viewModel()
        SideEffect { shared[entry.entry.route] = vm; individual[entry.id] = own }
        Button(onClick = { nav.forEntry(entry.id).popBackStack() }) { Text("BACK") }
    }

    @Composable private fun sharedModel(route: AppRoute): ProbeViewModel {
        val owner = LocalHomeViewModelStoreOwner.current
        val factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val repository = java.lang.reflect.Proxy.newProxyInstance(
                        OrderRepository::class.java.classLoader, arrayOf(OrderRepository::class.java),
                    ) { _, method, _ -> error("Navigation test must not call service ${method.name}") } as OrderRepository
                    return TodayOrderViewModel(repository) as T
                }
            }
        }
        val today: TodayOrderViewModel = viewModel(viewModelStoreOwner = owner, factory = factory)
        SideEffect { todayShared[route] = today }
        return viewModel(viewModelStoreOwner = owner)
    }

    @Test fun homeScopeIsSharedButEntriesAreIndependentAndResetClearsOwners() {
        compose.setContent { Content() }
        compose.onNodeWithText("PLANS").performClick()
        compose.onNodeWithText("BACK").assertExists()
        compose.runOnIdle {
            assertSame(shared[HomeRoute], shared[CarePlansListRoute])
            assertSame(todayShared[HomeRoute], todayShared[CarePlansListRoute])
            assertNotSame(shared[HomeRoute], individual.values.single())
        }
        compose.onNodeWithText("BACK").performClick()
        compose.onNodeWithText("RECORDS").performClick()
        compose.runOnIdle {
            assertSame(shared[HomeRoute], shared[ServiceRecordsListRoute])
            assertSame(todayShared[HomeRoute], todayShared[ServiceRecordsListRoute])
        }
        val old = shared.getValue(HomeRoute)
        compose.runOnIdle { navigator.resetToHome() }
        compose.onNodeWithText("HOME").assertExists()
        compose.runOnIdle { assertTrue(old.cleared); assertNotSame(old, shared[HomeRoute]) }
    }

    @Test fun unconsumedResultsAndEntryIdsRestoreAndConsumedResultsDoNotReplay() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { Content() }
        compose.onNodeWithText("CAMERA").performClick()
        compose.onNodeWithText("CAPTURE").performClick()
        compose.onNodeWithText("content://same").assertExists()
        val id = (navigator.backStack.single() as AppNavEntry).id
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("content://same").assertExists()
        compose.runOnIdle { assertEquals(id, (navigator.backStack.single() as AppNavEntry).id) }
        compose.onNodeWithText("CONSUME").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("EMPTY").assertExists()
        compose.onNodeWithText("CAMERA").performClick()
        compose.onNodeWithText("CAPTURE").performClick()
        compose.onNodeWithText("content://same").assertExists()
    }

    @Test fun activityRecreationRetainsHomeViewModelAndChildEntryAndResults() {
        // Both compositions must originate at the Activity's same setContent call site,
        // otherwise Compose correctly treats their saveable-state keys as different trees.
        compose.runOnIdle { NavigationStateTestActivity.recreatedContent = { Content() } }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("HOME").assertExists()
        compose.onNodeWithText("CAMERA").performClick()
        compose.onNodeWithText("CAPTURE").performClick()
        compose.onNodeWithText("PLANS").performClick()
        val oldHome = shared.getValue(HomeRoute)
        val oldToday = todayShared.getValue(HomeRoute)
        val oldEntries = navigator.backStack.toList()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("BACK").assertExists()
        compose.runOnIdle {
            assertEquals(oldEntries, navigator.backStack.toList())
            assertSame(oldHome, shared[CarePlansListRoute])
            assertSame(oldToday, todayShared[CarePlansListRoute])
            assertFalse(oldHome.cleared)
        }
        compose.onNodeWithText("BACK").performClick()
        compose.onNodeWithText("content://same").assertExists()
        compose.runOnIdle { assertSame(oldToday, todayShared[HomeRoute]) }
    }

    @Test fun evaluationPageSharesHomeOwnerAcrossRecreationWithoutReturningResults() {
        compose.runOnIdle { NavigationStateTestActivity.recreatedContent = { Content() } }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("HOME").assertExists()
        val form = WebViewRoute("https://mock.internal/form", "表单评估", isEvaluation = true)
        val originalHome = shared.getValue(HomeRoute)
        compose.runOnIdle { navigator.navigate(form) }
        compose.onNodeWithText("BACK").assertExists()
        compose.runOnIdle { assertSame(originalHome, shared[form]) }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("BACK").assertExists()
        compose.runOnIdle { assertSame(originalHome, shared[form]) }
        compose.onNodeWithText("BACK").performClick()
        compose.onNodeWithText("HOME").assertExists()
        compose.onNodeWithText("EMPTY").assertExists()
        compose.runOnIdle {
            assertSame(originalHome, shared[HomeRoute])
            assertEquals(1, navigator.backStack.size)
        }
    }

    private fun setMainGateContent(consented: Boolean, session: SessionState): androidx.lifecycle.ViewModelStore {
        val context = object : android.content.ContextWrapper(compose.activity) {
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences =
                baseContext.getSharedPreferences("navigation_gate_test_$name", mode)
        }
        val privacy = PrivacyConsentManager(context).apply {
            resetConsent()
            if (consented) markConsented()
        }
        val repository = object : UserSessionRepository {
            override val sessionState = kotlinx.coroutines.flow.MutableStateFlow(session)
            override suspend fun login(user: User) { sessionState.value = SessionState.LoggedIn(user) }
            override suspend fun updateUser(user: User) { login(user) }
            override suspend fun logout() { sessionState.value = SessionState.LoggedOut }
        }
        // No work is enqueued; the observer's empty active-work flow never calls a service.
        val observer = StartupUpdateWorkObserver(androidx.work.WorkManager.getInstance(context),
            DeviceUtils(context, context.getSharedPreferences("device", 0)))
        val vm = MainViewModel(repository, observer)
        val store = androidx.lifecycle.ViewModelStore().apply { put("gate", vm) }
        compose.setContent { MainApp(vm, privacy) }
        return store
    }

    @Test fun privacyGateDoesNotConstructBusinessNavigationEvenWithLoggedInSession() {
        val store = setMainGateContent(false, SessionState.LoggedIn(User(userId = 42)))
        try {
            compose.onNodeWithText("用户协议与隐私政策").assertExists()
            compose.onNodeWithText("确定登录").assertDoesNotExist()
            // This plain, non-Hilt host would fail if any business entry were constructed.
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun unknownSessionDoesNotConstructOrRestoreBusinessNavigation() {
        val store = setMainGateContent(true, SessionState.Unknown)
        try {
            compose.onNode(hasProgressBarRangeInfo(androidx.compose.ui.semantics.ProgressBarRangeInfo.Indeterminate)).assertExists()
            compose.onNodeWithText("确定登录").assertDoesNotExist()
            compose.onNodeWithText("用户协议与隐私政策").assertDoesNotExist()
        } finally { compose.runOnIdle { store.clear() } }
    }

    @Test fun repeatedIdentityDoesNotResetButLogoutAndAccountSwitchDo() {
        compose.setContent { Content() }
        compose.onNodeWithText("PLANS").performClick()
        compose.runOnIdle { identity.value = "user-1" }
        compose.onNodeWithText("BACK").assertExists()
        val old = shared.getValue(HomeRoute)
        compose.runOnIdle { identity.value = "user-2" }
        compose.onNodeWithText("HOME").assertExists()
        compose.runOnIdle { assertTrue(old.cleared) }
        compose.runOnIdle { identity.value = "anonymous" }
        compose.onNodeWithText("LOGIN").assertExists()
        compose.runOnIdle { assertFalse(navigator.popBackStack()) }
    }

    @Test fun predictiveBackCancellationKeepsEntryAndCompletionPopsExactlyOnce() {
        compose.setContent { Content() }
        compose.onNodeWithText("PLANS").performClick()
        val before = navigator.backStack.toList()
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(200f, 100f, 0.5f, BackEventCompat.EDGE_LEFT))
        }
        compose.runOnIdle { activity.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.onNodeWithText("BACK").assertExists()
        compose.runOnIdle { assertEquals(before, navigator.backStack.toList()) }
        compose.runOnIdle {
            activity.onBackPressedDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 100f, 0f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.dispatchOnBackProgressed(BackEventCompat(350f, 100f, 0.9f, BackEventCompat.EDGE_LEFT))
            activity.onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("HOME").assertExists()
        compose.runOnIdle { assertEquals(1, navigator.backStack.size) }
    }

    @Test fun manualFaceVerificationCancelAndSuccessAndNestedUploadKeepResultContracts() {
        compose.setContent { Content() }
        compose.onNodeWithText("MANUAL").performClick()
        compose.onNodeWithText("MANUAL SUCCESS").performClick()
        compose.onNodeWithText("FACE /mock/face.jpg / VERIFIED null / PHOTOS null").assertExists()
        compose.onNodeWithText("VERIFY").performClick()
        compose.onNodeWithText("VERIFY CANCEL").performClick()
        compose.onNodeWithText("FACE /mock/face.jpg / VERIFIED false / PHOTOS null").assertExists()
        compose.onNodeWithText("VERIFY").performClick()
        compose.onNodeWithText("VERIFY SUCCESS").performClick()
        compose.onNodeWithText("UPLOAD").performClick()
        compose.onNodeWithText("INPUT 1").assertExists()
        compose.onNodeWithText("NESTED CAMERA").performClick()
        compose.onNodeWithText("CAPTURE").performClick()
        compose.onNodeWithText("content://same").assertExists()
        compose.onNodeWithText("PUBLISH").performClick()
        compose.onNodeWithText("FACE /mock/face.jpg / VERIFIED true / PHOTOS 1").assertExists()
        compose.onNodeWithText("EMPTY").assertExists() // Nested camera must not write Home's mailbox.
    }

    @Test fun salesInternalBackHasPriorityAndCameraReturnPreservesSalesPage() {
        compose.setContent { Content() }
        compose.onNodeWithText("OPEN SALES REGISTRATION").performClick()
        compose.onNodeWithText("CAMERA").performClick()
        compose.onNodeWithText("CAPTURE").performClick()
        compose.onNodeWithText("SALES REGISTRATION", substring = false).assertExists()
        compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("SALES HOME").assertExists()
        compose.runOnIdle { assertEquals(1, navigator.backStack.size) }
    }
}
