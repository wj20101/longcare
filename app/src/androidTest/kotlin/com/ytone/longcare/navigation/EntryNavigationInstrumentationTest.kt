package com.ytone.longcare.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
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

    private fun TestNavHostController.hasEntry(route: Any): Boolean = when (route) {
        LoginRoute -> runCatching { getBackStackEntry(LoginRoute) }.isSuccess
        HomeRoute -> runCatching { getBackStackEntry(HomeRoute) }.isSuccess
        HomeGraphRoute -> runCatching { getBackStackEntry(HomeGraphRoute) }.isSuccess
        else -> error("Unsupported test route: $route")
    }

    private companion object {
        const val LOGIN_TAG = "entry-login"
        const val HOME_TAG = "entry-home"
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
