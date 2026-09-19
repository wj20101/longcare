package com.ytone.longcare.navigation

import androidx.navigation3.runtime.NavKey
import com.ytone.longcare.core.navigation.NavigationConstants
import com.ytone.longcare.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class AppNavigatorTest {
    @Test fun evaluationPresentationIsExplicitAndIndependentFromClosePurpose() {
        val nav = navigator()
        nav.navigateToEvaluationForm("https://evaluation.invalid/form", "任意标题")
        val form = nav.top().route as WebViewRoute
        assertTrue(form.isEvaluation)
        assertFalse(form.showNativeToolbar)
        nav.popBackStack()
        nav.navigateToEvaluationReport("https://evaluation.invalid/report", "任意标题")
        val report = nav.top().route as WebViewRoute
        assertFalse(report.isEvaluation)
        assertFalse(report.showNativeToolbar)
        nav.popBackStack()
        nav.navigateToWebView("https://evaluation.invalid/form", "任意标题")
        val ordinary = nav.top().route as WebViewRoute
        assertFalse(ordinary.isEvaluation)
        assertTrue(ordinary.showNativeToolbar)
        listOf(form, report, ordinary).forEach { route ->
            assertEquals(route, Json.decodeFromString<WebViewRoute>(Json.encodeToString(route)))
        }
    }

    @Test fun evaluationCloseReturnsToCallerAndLateCloseCannotPopNewPage() {
        val navigator = navigator()
        val homeId = navigator.top().id
        val route = WebViewRoute("https://evaluation.invalid/form", "表单评估", isEvaluation = true)
        assertEquals(route, Json.decodeFromString<WebViewRoute>(Json.encodeToString(route)))
        navigator.navigate(route)
        val form = navigator.page()
        assertTrue(form.canHandleCallback())
        assertTrue(form.popBackStack())
        assertEquals(homeId, navigator.top().id)
        navigator.navigate(WebViewRoute("https://evaluation.invalid/report", "报告"))
        assertFalse(form.canHandleCallback())
        assertFalse(form.popBackStack())
        assertEquals("报告", (navigator.top().route as WebViewRoute).title)
    }

    private val order = OrderNavParams(Long.MAX_VALUE, 7)
    private val camera = CameraRoute(WatermarkData("长护盾 & ? / 中文", "客户", "护理员", "a/b?x=中文#1"))
    private fun navigator(root: AppRoute = HomeRoute) = AppNavigator(mutableListOf(AppNavEntry(root)), NavigationResults())
    private fun AppNavigator.top() = backStack.last() as AppNavEntry
    private fun AppNavigator.page() = forEntry(top().id)

    @Test fun allRoutesRoundTripWithStableEntryAndCallerIds() {
        val routes = listOf(LoginRoute, HomeRoute, ServiceRoute(order), NursingExecutionRoute(order),
            WebViewRoute("https://example.test/a?b=中文&c=%2F#1", "条款 / ?"), SelectServiceRoute(order),
            WebViewRoute("https://evaluation.invalid/form", "评估", isEvaluation = true),
            PhotoUploadRoute(order), CarePlansListRoute, ServiceRecordsListRoute,
            IdentificationRoute(order),
            DefaultFaceVerificationRoute(order), UserListRoute("HAVE_SERVICE"),
            UserServiceRecordRoute(Long.MAX_VALUE, "测试", "中国 / \"街道\""), camera, ManualFaceCaptureRoute,
            NfcSignInRoute(order, SignInMode.START_ORDER),
            NfcSignInRoute(order, SignInMode.END_ORDER, EndOderInfo(listOf(1), listOf("中文"), endType = 2)),
            ServiceCountdownRoute(order), ServiceCountdownRoute(order, listOf(1, 2)),
            ServiceCompleteRoute(order, ServiceCompleteData(clientName = "用户", clientAge = 90)),
            EndServiceSelectionRoute(order, 2))
        val registry = AppEntryProviderBuilder().apply { registerAppNavGraphs(navigator()) }
        assertEquals(routes.map { it::class }.toSet(), registry.destinations.keys)
        routes.forEach { route ->
            val entry = AppNavEntry(route, callerId = "caller", homeId = "home")
            assertEquals(entry, Json.decodeFromString<AppNavEntry>(Json.encodeToString(entry)))
            assertNotEquals(entry.id, AppNavEntry(route).id)
        }
    }

    @Test fun rootAndEmptyStackCannotPop() {
        assertFalse(navigator().popBackStack())
        assertFalse(AppNavigator(mutableListOf(), NavigationResults()).popBackStack())
    }

    @Test fun loginDropsAnonymousHistoryAndStaleLoginCannotRepeat() {
        val nav = navigator(LoginRoute)
        val login = nav.page()
        login.navigateToHomeFromLogin()
        val home = nav.top()
        login.navigateToHomeFromLogin()
        assertEquals(listOf(home), nav.backStack)
        assertEquals(HomeRoute, home.route)
    }

    @Test fun sameArgumentsCreateDistinctEntries() {
        val nav = navigator()
        nav.navigate(camera)
        val first = nav.top()
        nav.navigate(camera)
        assertNotEquals(first.id, nav.top().id)
        assertEquals(first.id, nav.top().callerId)
    }

    @Test fun identificationReplacesSourceAndKeepsEarlierCaller() {
        val nav = navigator()
        val home = nav.top()
        nav.navigate(ServiceRoute(order))
        val source = nav.page()
        source.navigateToIdentification(order)
        assertEquals(listOf(HomeRoute, IdentificationRoute(order)), nav.backStack.map { (it as AppNavEntry).route })
        assertEquals(home.id, nav.top().callerId)
        source.popBackStack()
        assertEquals(2, nav.backStack.size)
    }

    @Test fun completionPreservesHomeAndBackReturnsHomeAsConfirmed() {
        val nav = navigator()
        val home = nav.top()
        nav.navigate(ServiceRoute(order))
        nav.navigate(IdentificationRoute(order))
        nav.page().navigateToServiceComplete(order, ServiceCompleteData())
        assertEquals(2, nav.backStack.size)
        assertEquals(home, nav.backStack.first())
        assertTrue(nav.popBackStack())
        assertEquals(home, nav.top())
    }

    @Test fun homeResetCreatesNewScopeAndClearsResults() {
        val nav = navigator()
        val old = nav.top()
        nav.results.handle(old.id).set(NavigationConstants.CAPTURED_IMAGE_URI_KEY, "file://old")
        nav.navigate(camera)
        nav.page().resetToHome()
        assertNotEquals(old.id, nav.top().id)
        assertEquals("{}", nav.results.snapshot())
    }

    @Test fun nestedCameraAndUploadResultsUseExactCallerAndSurviveRestore() {
        val nav = navigator(ServiceCountdownRoute(order))
        val countdown = nav.top()
        val input = mapOf(ImageTaskType.BEFORE_CARE to listOf(ImageTask("图片", "content://中文", ImageTaskType.BEFORE_CARE,
            "file://result", ImageTaskStatus.SUCCESS, "错误?", true, "cloud/key", "https://example.test/photo")))
        nav.page().handle(countdown).results.set(NavigationConstants.EXISTING_IMAGES_KEY, input)
        nav.navigate(PhotoUploadRoute(order))
        val upload = nav.top()
        nav.navigate(camera)
        val cameraPage = nav.page()
        cameraPage.previousBackStackEntry!!.results.set(NavigationConstants.CAPTURED_IMAGE_URI_KEY, "content://camera")
        cameraPage.popBackStack()
        val restoredStack = Json.decodeFromString<List<AppNavEntry>>(Json.encodeToString(nav.backStack.map { it as AppNavEntry }))
        val restored = AppNavigator(restoredStack.toMutableList<NavKey>(), NavigationResults.restore(nav.results.snapshot()))
        val uploadPage = restored.page()
        assertEquals(upload.id, restored.top().id)
        assertEquals("content://camera", uploadPage.handle(upload).results.getStateFlow<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY, null).value)
        assertEquals(input, uploadPage.previousBackStackEntry!!.results.getStateFlow<Map<ImageTaskType, List<ImageTask>>>(NavigationConstants.EXISTING_IMAGES_KEY, null).value)
        uploadPage.previousBackStackEntry!!.results.set(NavigationConstants.PHOTO_UPLOAD_RESULT_KEY, input)
        uploadPage.popBackStack()
        assertEquals(countdown.id, restored.top().id)
    }

    @Test fun consumingEmitsNullAndSameValueCanBeDeliveredAgain() {
        val store = NavigationResults()
        val handle = store.handle("caller")
        val key = NavigationConstants.CAPTURED_IMAGE_URI_KEY
        val flow = handle.getStateFlow<String>(key, null)
        handle.set(key, "content://same")
        assertEquals("content://same", flow.value)
        handle.remove<String>(key)
        assertNull(flow.value)
        assertNull(NavigationResults.restore(store.snapshot()).handle("caller").getStateFlow<String>(key, null).value)
        handle.set(key, "content://same")
        assertEquals("content://same", flow.value)
    }

    @Test fun lateProducerCannotWriteIntoANewCallerOrPopItsPage() {
        val nav = navigator()
        nav.navigate(camera)
        val old = nav.page()
        val oldCaller = old.previousBackStackEntry!!
        old.popBackStack()
        nav.navigate(camera)
        oldCaller.results.set(NavigationConstants.CAPTURED_IMAGE_URI_KEY, "late")
        old.popBackStack()
        assertNull(oldCaller.results.getStateFlow<String>(NavigationConstants.CAPTURED_IMAGE_URI_KEY, null).value)
        assertEquals(2, nav.backStack.size)
    }

    @Test fun differentEntryMailboxesNeverShareAndDropClearsSubscribers() {
        val store = NavigationResults()
        val a = store.handle("a")
        val b = store.handle("b")
        val key = NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY
        val flow = a.getStateFlow<Boolean>(key, null)
        a.set(key, false)
        assertEquals(false, flow.value)
        assertNull(b.getStateFlow<Boolean>(key, null).value)
        store.drop("a")
        assertNull(flow.value)
    }
}
