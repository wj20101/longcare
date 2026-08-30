package com.ytone.longcare.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.typeOf

@RunWith(AndroidJUnit4::class)
class EntryNavigationInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: TestNavHostController

    @Test
    fun loggedOutColdStartUsesTypedLoginRoute() {
        setEntryHost(AuthenticationRoot.Login)

        composeRule.onNodeWithTag(LOGIN_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                navController.getBackStackEntry(LoginRoute).destination,
                navController.currentDestination,
            )
            assertFalse(navController.hasEntry(HomeGraphRoute))
        }
    }

    @Test
    fun loggedInColdStartUsesTypedHomeGraphWithoutLogin() {
        setEntryHost(AuthenticationRoot.Home)

        composeRule.onNodeWithTag(HOME_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                navController.getBackStackEntry(HomeRoute).destination,
                navController.currentDestination,
            )
            assertTrue(navController.hasEntry(HomeGraphRoute))
            assertFalse(navController.hasEntry(LoginRoute))
        }
    }

    @Test
    fun loginCallbackAndSessionEmissionProduceOneHomeGraph() {
        val targetRoot = setEntryHost(AuthenticationRoot.Login)

        composeRule.onNodeWithTag(LOGIN_TAG).performClick()
        composeRule.waitForIdle()
        val homeEntryId = composeRule.runOnIdle {
            assertFalse(navController.hasEntry(LoginRoute))
            navController.getBackStackEntry(HomeGraphRoute).id
        }

        composeRule.runOnIdle { targetRoot.value = AuthenticationRoot.Home }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            assertEquals(homeEntryId, navController.getBackStackEntry(HomeGraphRoute).id)
            assertEquals(
                AuthenticationNavigationCommand.NoOp,
                reconcileAuthenticationRoot(navController, AuthenticationRoot.Home),
            )
            assertEquals(homeEntryId, navController.getBackStackEntry(HomeGraphRoute).id)
        }
    }

    @Test
    fun unresolvedOrLoggedOutSessionDoesNotLeaveLoginRoot() {
        val targetRoot = setEntryHost(AuthenticationRoot.Login)

        composeRule.runOnIdle { targetRoot.value = null }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LOGIN_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.hasEntry(LoginRoute))
            assertFalse(navController.hasEntry(HomeGraphRoute))
        }

        composeRule.runOnIdle { targetRoot.value = AuthenticationRoot.Login }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LOGIN_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertTrue(navController.hasEntry(LoginRoute))
            assertFalse(navController.hasEntry(HomeGraphRoute))
        }
    }

    @Test
    fun logoutClearsProtectedHistoryAndBackCannotCrossBoundary() {
        val targetRoot = setEntryHost(AuthenticationRoot.Home)
        val oldHomeEntry = composeRule.runOnIdle {
            val entry = navController.getBackStackEntry(HomeGraphRoute)
            entry.savedStateHandle[OWNER_ACCOUNT_KEY] = "old-account"
            navController.navigate(HomeRoute)
            entry
        }

        composeRule.runOnIdle { targetRoot.value = AuthenticationRoot.Login }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(LOGIN_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                navController.getBackStackEntry(LoginRoute).destination,
                navController.currentDestination,
            )
            assertFalse(navController.hasEntry(HomeGraphRoute))
            assertFalse(navController.hasEntry(HomeRoute))
            assertFalse(navController.popBackStack())
            assertEquals(Lifecycle.State.DESTROYED, oldHomeEntry.lifecycle.currentState)
        }

        composeRule.runOnIdle { targetRoot.value = AuthenticationRoot.Home }
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            val newHomeEntry = navController.getBackStackEntry(HomeGraphRoute)
            assertNotEquals(oldHomeEntry.id, newHomeEntry.id)
            assertNull(newHomeEntry.savedStateHandle.get<String>(OWNER_ACCOUNT_KEY))
        }
    }

    @Test
    fun startOrderNavigatesDirectlyToNfcAndBackToOrigin() {
        val orderParams = OrderNavParams(orderId = 987654321L, planId = 42)
        val expectedRoute = startOrderNfcSignInRoute(orderParams)
        setStartOrderHost(orderParams)

        composeRule.onNodeWithTag(START_ORDER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NFC_START_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                navController.getBackStackEntry(HomeRoute).destination,
                navController.previousBackStackEntry?.destination,
            )
            assertEquals(
                expectedRoute,
                navController.currentBackStackEntry?.toRoute<NfcSignInRoute>(),
            )
        }

        composeRule.onNodeWithTag(NFC_BACK_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(START_ORDER_TAG).assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(
                navController.getBackStackEntry(HomeRoute).destination,
                navController.currentDestination,
            )
            assertFalse(runCatching { navController.getBackStackEntry(expectedRoute) }.isSuccess)
        }
    }

    private fun setEntryHost(
        startRoot: AuthenticationRoot,
    ): MutableState<AuthenticationRoot?> {
        val targetRoot = mutableStateOf<AuthenticationRoot?>(startRoot)
        composeRule.setContent {
            val context = LocalContext.current
            val controller = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            navController = controller
            AppNavHost(
                navController = controller,
                startRoot = startRoot,
                targetRoot = targetRoot.value,
                entryDestinationRenderers = markerRenderers,
            )
        }
        composeRule.waitForIdle()
        return targetRoot
    }

    private fun setStartOrderHost(orderParams: OrderNavParams) {
        composeRule.setContent {
            val context = LocalContext.current
            val controller = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            navController = controller
            NavHost(
                navController = controller,
                startDestination = HomeRoute,
            ) {
                composable<HomeRoute> {
                    Button(
                        modifier = Modifier.testTag(START_ORDER_TAG),
                        onClick = { controller.navigateToNfcSignInForStartOrder(orderParams) },
                    ) {
                        Text("Start order")
                    }
                }
                composable<NfcSignInRoute>(
                    typeMap = mapOf(
                        typeOf<EndOderInfo?>() to EndOderInfoNavType,
                        typeOf<OrderNavParams>() to OrderNavParamsNavType,
                    ),
                ) {
                    Column {
                        Text(
                            modifier = Modifier.testTag(NFC_START_TAG),
                            text = "NFC start",
                        )
                        Button(
                            modifier = Modifier.testTag(NFC_BACK_TAG),
                            onClick = { controller.popBackStack() },
                        ) {
                            Text("Back")
                        }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun TestNavHostController.hasEntry(route: Any): Boolean = when (route) {
        LoginRoute -> runCatching { getBackStackEntry(LoginRoute) }.isSuccess
        HomeRoute -> runCatching { getBackStackEntry(HomeRoute) }.isSuccess
        HomeGraphRoute -> runCatching { getBackStackEntry(HomeGraphRoute) }.isSuccess
        else -> error("Unsupported test route: $route")
    }

    private companion object {
        const val LOGIN_TAG = "entry-login"
        const val HOME_TAG = "entry-home"
        const val START_ORDER_TAG = "entry-start-order"
        const val NFC_START_TAG = "entry-nfc-start"
        const val NFC_BACK_TAG = "entry-nfc-back"
        const val OWNER_ACCOUNT_KEY = "owner-account"

        val markerRenderers = EntryDestinationRenderers(
            login = { _, onLoginSuccess ->
                Button(
                    modifier = Modifier.testTag(LOGIN_TAG),
                    onClick = onLoginSuccess,
                ) {
                    Text("Login")
                }
            },
            home = { _, _ ->
                Text(
                    modifier = Modifier.testTag(HOME_TAG),
                    text = "Home",
                )
            },
        )
    }
}
