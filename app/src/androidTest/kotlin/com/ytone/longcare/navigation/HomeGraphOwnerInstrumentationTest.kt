package com.ytone.longcare.navigation

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.testing.TestNavHostController
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeGraphOwnerInstrumentationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun homeAndOrderChildResolveTheSameGraphOwner() {
        lateinit var homeOwner: NavBackStackEntry
        lateinit var orderOwner: NavBackStackEntry
        composeRule.setContent {
            val context = LocalContext.current
            val navController = remember {
                TestNavHostController(context).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
            }
            NavHost(
                navController = navController,
                startDestination = HomeGraphRoute,
            ) {
                navigation<HomeGraphRoute>(startDestination = HomeRoute) {
                    composable<HomeRoute> {
                        homeOwner = navController.requireHomeGraphBackStackEntry()
                        Button(
                            modifier = Modifier.testTag(OPEN_ORDER_TAG),
                            onClick = { navController.navigate(CarePlansListRoute) },
                        ) {
                            Text("Open order")
                        }
                    }
                    composable<CarePlansListRoute> {
                        orderOwner = navController.requireHomeGraphBackStackEntry()
                        Text("Order")
                    }
                }
            }
        }

        composeRule.onNodeWithTag(OPEN_ORDER_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertSame(homeOwner, orderOwner) }
    }

    @Test
    fun missingHomeGraphFailsExplicitly() {
        lateinit var navController: TestNavHostController
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
                startDestination = LoginRoute,
            ) {
                composable<LoginRoute> { Text("Login") }
            }
        }

        composeRule.runOnIdle {
            val failure = assertThrows(IllegalStateException::class.java) {
                navController.requireHomeGraphBackStackEntry()
            }
            assertTrue(failure.message.orEmpty().contains("HomeGraphRoute"))
        }
    }

    private companion object {
        const val OPEN_ORDER_TAG = "open-order"
    }
}
