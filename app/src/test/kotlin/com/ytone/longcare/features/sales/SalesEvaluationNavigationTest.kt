package com.ytone.longcare.features.sales

import com.ytone.longcare.presentation.sales.SalesNavigationState
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.presentation.sales.evaluationBackTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesEvaluationNavigationTest {

    @Test
    fun `device flow returns through choice page to submission result`() {
        val choiceParent = SalesPage.SUBMIT_SUCCESS

        assertEquals(
            SalesPage.EVALUATION_CHOICE,
            evaluationBackTarget(
                currentPage = SalesPage.DEVICE_STATUS,
                choiceReturnPage = choiceParent,
            ),
        )
        assertEquals(
            SalesPage.SUBMIT_SUCCESS,
            evaluationBackTarget(
                currentPage = SalesPage.EVALUATION_CHOICE,
                choiceReturnPage = choiceParent,
            ),
        )
    }

    @Test
    fun `choice page never returns to itself`() {
        assertEquals(
            SalesPage.HOME,
            evaluationBackTarget(
                currentPage = SalesPage.EVALUATION_CHOICE,
                choiceReturnPage = SalesPage.EVALUATION_CHOICE,
            ),
        )
    }

    @Test
    fun `navigation holder keeps return targets and resets root state`() {
        val state = SalesNavigationState()

        state.selectRootTab(2)
        state.showCustomerDetail(SalesPage.CUSTOMERS)
        state.rememberEvaluationChoiceReturnPage(SalesPage.CUSTOMER_DETAIL)

        assertEquals(SalesPage.CUSTOMER_DETAIL, state.currentPage)
        assertEquals(SalesPage.CUSTOMERS, state.detailReturnPage)
        assertEquals(SalesPage.CUSTOMER_DETAIL, state.evaluationChoiceReturnPage)

        state.goHome()

        assertEquals(SalesPage.HOME, state.currentPage)
        assertEquals(0, state.rootTab)
    }
}
