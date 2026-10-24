package com.ytone.longcare.features.selectservice.ui

import android.app.Application
import android.content.Intent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.view
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
import com.ytone.longcare.common.utils.PermissionGuideId
import com.ytone.longcare.common.utils.PermissionGuideItem
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.domain.location.LocationAcquisition
import com.ytone.longcare.domain.location.LocationFacade
import com.ytone.longcare.domain.order.OrderRepository
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.selectservice.api.SelectServiceActions
import com.ytone.longcare.features.selectservice.vm.SelectServiceViewModel
import com.ytone.longcare.model.LocationResult
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderInfoModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.result.ApiResult
import com.ytone.longcare.shared.vm.SharedOrderDetailViewModel
import com.ytone.longcare.shared.vm.StarOrderUiState
import com.ytone.longcare.theme.LongCareTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.just
import io.mockk.Runs
import io.mockk.unmockkObject
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SelectServiceStartLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val details = mockk<OrderDetailRepository>(relaxed = true)
    private val orders = mockk<OrderRepository>()
    private val location = mockk<LocationFacade>()
    private val config = mockk<SystemConfigManager>()
    private val key = OrderKey(42, 7)
    private val sample = LocationResult(31.0, 121.0, "test")
    private val acquired = CompletableDeferred<LocationAcquisition>()
    private val visible = mutableStateOf(true)
    private val generation = mutableStateOf(1)
    private val navigations = mutableListOf<Triple<Int, OrderKey, List<Int>>>()
    private lateinit var model: SharedOrderDetailViewModel
    private lateinit var owner: TestOwner
    private lateinit var restoration: StateRestorationTester
    private var guides = emptyList<PermissionGuideItem>()
    private var allGuides: List<PermissionGuideItem>? = null

    @Before fun setUp() {
        mockkObject(DeviceCompatibilityHelper)
        every { DeviceCompatibilityHelper.getAllRequiredGuides(any()) } answers { guides }
        every { DeviceCompatibilityHelper.getAllGuides(any()) } answers { allGuides ?: guides }
        every { DeviceCompatibilityHelper.markGuideShown(any(), any()) } just Runs
        every { DeviceCompatibilityHelper.openGuide(any(), any()) } returns true
        coEvery { config.getSelectServiceType() } returns 1
        coEvery { details.getOrderInfo(key, any()) } returns ApiResult.Success(
            ServiceOrderInfoModel(orderId = 42, projectList = listOf(ServiceProjectM(projectId = 9))),
        )
        coEvery { location.acquireServiceOrderLocation() } coAnswers { acquired.await() }
        every { location.serviceOrderLocationFailure(sample) } returns null
        coEvery { orders.starOrder(any(), any(), any(), any()) } returns ApiResult.Success(Unit)
        compose.runOnIdle {
            owner = TestOwner().apply { registry.currentState = Lifecycle.State.RESUMED }
            model = SharedOrderDetailViewModel(details, orders, location,
                ResourceTextResolver(compose.activity.applicationContext), mockk(relaxed = true),
                { com.ytone.longcare.model.ServiceTimeSnapshot(System.currentTimeMillis(), 0L, null) })
        }
        val selection = SelectServiceViewModel(config)
        restoration = StateRestorationTester(compose)
        restoration.setContent {
            // Keep the host attached while replacing only the page, as Navigation does.
            Box {
                if (visible.value) {
                    val currentGeneration = generation.value
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        LongCareTheme {
                            SelectServiceScreen(
                                actions = SelectServiceActions({}, { order, projects ->
                                    navigations += Triple(currentGeneration, order, projects)
                                }),
                                orderKey = key,
                                selectServiceViewModel = selection,
                                sharedViewModel = model,
                            )
                        }
                    }
                }
            }
        }
    }

    @After fun tearDown() = unmockkObject(DeviceCompatibilityHelper)

    @Test fun recreatedCompositionConsumesServerSuccessThroughItsCurrentNavigationAction() {
        clickStartService()
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { acquired.complete(LocationAcquisition.Success(sample)) }
        compose.waitUntil { model.starOrderState.value is StarOrderUiState.Success }
        compose.runOnIdle {
            assertEquals(emptyList<Triple<Int, OrderKey, List<Int>>>(), navigations)
            generation.value = 2
            visible.value = true
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(Lifecycle.State.RESUMED, owner.registry.currentState) }
        compose.waitUntil { navigations.isNotEmpty() }
        compose.runOnIdle {
            assertEquals(listOf(Triple(2, key, listOf(9))), navigations)
            assertEquals(StarOrderUiState.Initial, model.starOrderState.value)
        }
        coVerify(exactly = 1) { details.updateSelectedProjects(key, listOf(9)) }
        coVerify(exactly = 1) { orders.starOrder(42, listOf(9L), "121.0", "31.0") }
    }

    @Test fun backgroundSuccessWaitsForResumeAndIsConsumedOnce() {
        clickStartService()
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            acquired.complete(LocationAcquisition.Success(sample))
        }
        compose.waitUntil { model.starOrderState.value is StarOrderUiState.Success }
        compose.runOnIdle {
            assertEquals(emptyList<Triple<Int, OrderKey, List<Int>>>(), navigations)
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitUntil { navigations.isNotEmpty() }
        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.runOnIdle { assertEquals(listOf(Triple(1, key, listOf(9))), navigations) }
    }

    @Test fun confirming_background_recommendations_starts_the_selected_service_once() {
        prepareGuide()
        clickStartService()
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }

        confirmStartService()
        compose.waitForIdle()
        coVerify(exactly = 1) { location.acquireServiceOrderLocation() }
        io.mockk.verify(exactly = 1) {
            DeviceCompatibilityHelper.markGuideShown(any(), PermissionGuideId.BATTERY_OPTIMIZATION)
        }
        compose.runOnIdle { acquired.complete(LocationAcquisition.Success(sample)) }
        compose.waitUntil { navigations.isNotEmpty() }
        coVerify(exactly = 1) { orders.starOrder(42, listOf(9L), "121.0", "31.0") }
        compose.runOnIdle { assertEquals(listOf(Triple(1, key, listOf(9))), navigations) }
    }

    @Test fun opening_background_settings_does_not_start_service_or_mark_the_guide_as_allowed() {
        prepareGuide()
        clickStartService()
        val actionDescription = compose.activity.getString(
            com.ytone.longcare.core.ui.R.string.background_settings_open_description,
            compose.activity.getString(guides.single().titleRes),
        )
        compose.onNodeWithContentDescription(actionDescription).performClick()
        compose.waitForIdle()
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 1) { DeviceCompatibilityHelper.openGuide(any(), guides.single()) }
        io.mockk.verify(exactly = 0) { DeviceCompatibilityHelper.markGuideShown(any(), any()) }
        compose.onNode(hasText("开始服务") and hasAnyAncestor(isDialog())).assertExists()
    }

    @Test fun failed_background_settings_action_shows_one_message_and_successful_retry_does_not_repeat_it() {
        prepareGuide()
        every { DeviceCompatibilityHelper.openGuide(any(), any()) } returns false
        clickStartService()
        compose.runOnIdle { ShadowToast.reset() }
        val actionDescription = compose.activity.getString(
            com.ytone.longcare.core.ui.R.string.background_settings_open_description,
            compose.activity.getString(guides.single().titleRes),
        )

        compose.onNodeWithContentDescription(actionDescription).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(1, ShadowToast.shownToastCount())
            assertEquals(compose.activity.getString(com.ytone.longcare.core.ui.R.string.permission_settings_unavailable),
                ShadowToast.getTextOfLatestToast())
        }
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 0) { DeviceCompatibilityHelper.markGuideShown(any(), any()) }
        compose.onNode(hasText("开始服务") and hasAnyAncestor(isDialog())).assertExists()

        every { DeviceCompatibilityHelper.openGuide(any(), any()) } returns true
        compose.onNodeWithContentDescription(actionDescription).performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(1, ShadowToast.shownToastCount()) }
        io.mockk.verify(exactly = 2) { DeviceCompatibilityHelper.openGuide(any(), guides.single()) }
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 0) { DeviceCompatibilityHelper.markGuideShown(any(), any()) }
        compose.onNode(hasText("开始服务") and hasAnyAncestor(isDialog())).assertExists()
    }

    @Test fun automatic_recommendations_refresh_status_without_adding_previously_seen_items() {
        prepareGuide()
        val alreadyShown = guides.single().copy(
            id = PermissionGuideId.MANUFACTURER_POPUP,
            titleRes = com.ytone.longcare.core.ui.R.string.common_retry,
        )
        allGuides = guides + alreadyShown
        clickStartService()
        compose.onNodeWithText("查看机型设置说明").performScrollTo().performClick()
        compose.onNodeWithText("重试").assertDoesNotExist()

        compose.runOnIdle {
            owner.registry.currentState = Lifecycle.State.STARTED
            allGuides = listOf(
                guides.single().copy(statusRes = com.ytone.longcare.core.ui.R.string.common_confirm),
                alreadyShown,
            )
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.onNodeWithText("确认").performScrollTo().assertExists()
        compose.onNodeWithText("重试").assertDoesNotExist()
        confirmStartService()
        compose.waitForIdle()
        io.mockk.verify(exactly = 1) {
            DeviceCompatibilityHelper.markGuideShown(any(), PermissionGuideId.BATTERY_OPTIMIZATION)
        }
        io.mockk.verify(exactly = 0) {
            DeviceCompatibilityHelper.markGuideShown(any(), PermissionGuideId.MANUFACTURER_POPUP)
        }
        coVerify(exactly = 1) { location.acquireServiceOrderLocation() }
    }

    @Test fun cancelling_the_guide_does_not_start_service_and_respects_the_shown_recommendation() {
        prepareGuide()
        clickStartService()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 1) {
            DeviceCompatibilityHelper.markGuideShown(any(), PermissionGuideId.BATTERY_OPTIMIZATION)
        }

        // The real helper omits an already shown recommendation, without treating it as granted.
        compose.runOnIdle { guides = emptyList() }
        clickStartService()
        coVerify(exactly = 1) { location.acquireServiceOrderLocation() }
    }

    @Test fun back_from_the_guide_does_not_start_service() {
        prepareGuide()
        clickStartService()
        val dialogView = requireNotNull(compose.onNode(isDialog()).fetchSemanticsNode().layoutInfo.view)
        compose.runOnIdle {
            val provider = generateSequence(dialogView) { it.parent as? View }
                .filterIsInstance<DialogWindowProvider>().first()
            // Route Back through the real dialog window, rather than Compose's focused key node.
            (provider.window.callback as ComponentDialog).onBackPressedDispatcher.onBackPressed()
        }
        compose.onNodeWithText("后台运行设置").assertDoesNotExist()
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }
        io.mockk.verify(exactly = 1) {
            DeviceCompatibilityHelper.markGuideShown(any(), PermissionGuideId.BATTERY_OPTIMIZATION)
        }
    }

    @Test fun pending_selection_and_guide_identities_restore_without_starting_until_confirmed() {
        prepareGuide()
        clickStartService()
        allGuides = guides + guides.single().copy(
            id = PermissionGuideId.MANUFACTURER_POPUP,
            titleRes = com.ytone.longcare.core.ui.R.string.common_retry,
        )
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("后台运行设置").assertExists()
        compose.onNodeWithText("查看机型设置说明").performScrollTo().performClick()
        compose.onNodeWithText("重试").assertDoesNotExist()
        coVerify(exactly = 0) { location.acquireServiceOrderLocation() }
        coVerify(exactly = 0) { orders.starOrder(any(), any(), any(), any()) }

        confirmStartService()
        compose.runOnIdle { acquired.complete(LocationAcquisition.Success(sample)) }
        compose.waitUntil { navigations.isNotEmpty() }
        coVerify(exactly = 1) { orders.starOrder(42, listOf(9L), "121.0", "31.0") }
        compose.runOnIdle { assertEquals(listOf(Triple(1, key, listOf(9))), navigations) }
    }

    private fun prepareGuide() {
        guides = listOf(PermissionGuideItem(
            id = PermissionGuideId.BATTERY_OPTIMIZATION,
            titleRes = com.ytone.longcare.core.ui.R.string.background_settings_action,
            messageRes = com.ytone.longcare.core.ui.R.string.location_error_disabled,
            settingsIntent = Intent("com.ytone.longcare.test.BACKGROUND_SETTINGS"),
        ))
    }

    private fun clickStartService() {
        compose.runOnIdle { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        compose.onNodeWithText("开始服务").performClick()
    }

    private fun confirmStartService() {
        compose.onNode(hasText("开始服务") and hasAnyAncestor(isDialog())).performClick()
    }

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
