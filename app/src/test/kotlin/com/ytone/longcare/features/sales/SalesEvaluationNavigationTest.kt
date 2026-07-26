package com.ytone.longcare.features.sales

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
}
