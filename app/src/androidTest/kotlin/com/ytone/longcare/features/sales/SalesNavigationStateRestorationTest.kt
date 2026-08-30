package com.ytone.longcare.features.sales

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ytone.longcare.presentation.sales.SalesNavigationSnapshot
import com.ytone.longcare.presentation.sales.SalesNavigationState
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.presentation.sales.reduceSalesBack
import com.ytone.longcare.presentation.sales.rememberSalesNavigationState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SalesNavigationStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun nonRootSnapshotRestoresAndKeepsItsBackPath() {
        val restorationTester = StateRestorationTester(composeRule)
        lateinit var navigationState: SalesNavigationState
        restorationTester.setContent {
            navigationState = rememberSalesNavigationState()
            Text(navigationState.currentPage.name)
        }

        val expected = SalesNavigationSnapshot(
            currentPage = SalesPage.EVALUATION_GUIDE,
            rootTab = 2,
            detailReturnPage = SalesPage.CUSTOMERS,
            evaluationChoiceReturnPage = SalesPage.SUBMIT_SUCCESS,
            reminderIndex = 4,
        )
        composeRule.runOnIdle {
            navigationState.selectRootTab(2)
            navigationState.showCustomerDetail(SalesPage.CUSTOMERS)
            navigationState.rememberEvaluationChoiceReturnPage(SalesPage.SUBMIT_SUCCESS)
            navigationState.selectReminder(4)
            navigationState.navigate(SalesPage.EVALUATION_GUIDE)
        }
        composeRule.onNodeWithText(SalesPage.EVALUATION_GUIDE.name).assertExists()

        restorationTester.emulateSavedInstanceStateRestore()

        composeRule.runOnIdle {
            assertEquals(expected, navigationState.snapshot())
            navigationState.apply(reduceSalesBack(navigationState.snapshot()).snapshot)
        }
        composeRule.onNodeWithText(SalesPage.DEVICE_STATUS.name).assertExists()
    }
}
