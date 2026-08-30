package com.ytone.longcare.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.testing.TestNavHostController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.core.navigation.NavigationConstants
import kotlin.reflect.typeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IdentificationPostVerificationNavigationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController
    private lateinit var identificationEntry: NavBackStackEntry

    @Test
    fun testOwnedVerifiedResult_reachesSelectionAndCountdownWithoutFaceSdkBypass() {
        val orderParams = OrderNavParams(orderId = 8_321L, planId = 14)
        val selectedProjectIds = listOf(11, 17)
        setContent(orderParams, selectedProjectIds)

        composeRule.onNodeWithTag(CONTINUE_TAG).assertIsNotEnabled()
        composeRule.runOnIdle {
            identificationEntry.savedStateHandle[
                NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY
            ] = true
        }

        composeRule.onNodeWithTag(CONTINUE_TAG).assertIsEnabled().performClick()
        composeRule.onNodeWithTag(SELECT_SERVICE_TAG).assertIsDisplayed().performClick()
        composeRule.onNodeWithTag(COUNTDOWN_TAG).assertIsDisplayed()

        composeRule.runOnIdle {
            assertNull(
                identificationEntry.savedStateHandle.get<Boolean>(
                    NavigationConstants.DEFAULT_FACE_VERIFICATION_RESULT_KEY,
                ),
            )
            assertEquals(
                ServiceCountdownRoute(
                    orderParams = orderParams,
                    projectIdList = selectedProjectIds,
                ),
                navController.currentBackStackEntry?.toRoute<ServiceCountdownRoute>(),
            )
        }
    }

    private fun setContent(orderParams: OrderNavParams, selectedProjectIds: List<Int>) {
        composeRule.setContent {
            val context = LocalContext.current
            val controller =
                remember {
                    TestNavHostController(context).apply {
                        navigatorProvider.addNavigator(ComposeNavigator())
                    }
                }
            navController = controller
            NavHost(
                navController = controller,
                startDestination = IdentificationRoute(orderParams),
            ) {
                composable<IdentificationRoute>(
                    typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType),
                ) { entry ->
                    identificationEntry = entry
                    val actions =
                        createIdentificationRouteActions(
                            savedStateHandle = entry.savedStateHandle,
                            onNavigateBack = {},
                            onNavigateToCamera = {},
                            onNavigateToManualFaceCapture = {},
                            onNavigateToDefaultFaceVerification = {},
                            onNavigateToSelectService = controller::navigateToSelectService,
                        )
                    val verified by
                        actions.defaultFaceVerificationResultFlow.collectAsStateWithLifecycle()
                    Button(
                        modifier = Modifier.testTag(CONTINUE_TAG),
                        enabled = verified == true,
                        onClick = {
                            actions.clearDefaultFaceVerificationResult()
                            actions.onNavigateToSelectService(orderParams.toOrderKey())
                        },
                    ) {
                        Text("Continue after verified identity")
                    }
                }
                composable<SelectServiceRoute>(
                    typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType),
                ) { entry ->
                    val route = entry.toRoute<SelectServiceRoute>()
                    Button(
                        modifier = Modifier.testTag(SELECT_SERVICE_TAG),
                        onClick = {
                            controller.navigateToServiceCountdown(
                                route.orderParams,
                                selectedProjectIds,
                            )
                        },
                    ) {
                        Text("Select services")
                    }
                }
                composable<ServiceCountdownRoute>(
                    typeMap = mapOf(typeOf<OrderNavParams>() to OrderNavParamsNavType),
                ) {
                    Text(
                        modifier = Modifier.testTag(COUNTDOWN_TAG),
                        text = "Service countdown",
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val CONTINUE_TAG = "identification-test-owned-verified"
        const val SELECT_SERVICE_TAG = "identification-select-service"
        const val COUNTDOWN_TAG = "identification-service-countdown"
    }
}
