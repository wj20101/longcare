package com.ytone.longcare.features.selectservice.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ytone.longcare.common.text.ResourceTextResolver
import com.ytone.longcare.common.utils.DeviceCompatibilityHelper
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

    @Before fun setUp() {
        mockkObject(DeviceCompatibilityHelper)
        every { DeviceCompatibilityHelper.getAllRequiredGuides(any()) } returns emptyList()
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
                ResourceTextResolver(compose.activity.applicationContext), mockk(relaxed = true))
        }
        val selection = SelectServiceViewModel(config)
        compose.setContent {
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
        compose.runOnIdle { ShadowSystemClock.advanceBy(Duration.ofSeconds(1)) }
        compose.onNodeWithText("开始服务").performClick()
    }

    @After fun tearDown() = unmockkObject(DeviceCompatibilityHelper)

    @Test fun recreatedCompositionConsumesServerSuccessThroughItsCurrentNavigationAction() {
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

    private class TestOwner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
