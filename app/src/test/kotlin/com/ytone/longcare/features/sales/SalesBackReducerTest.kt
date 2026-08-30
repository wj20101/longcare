package com.ytone.longcare.features.sales

import com.ytone.longcare.presentation.sales.SalesBackEffect
import com.ytone.longcare.presentation.sales.SalesNavigationSnapshot
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.presentation.sales.reduceSalesBack
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesBackReducerTest {
    @Test
    fun rootAndReminderPagesUseTheirDeclaredParents() {
        assertBack(
            from = SalesNavigationSnapshot(currentPage = SalesPage.HOME, rootTab = 2),
            expectedPage = SalesPage.HOME,
            expectedRootTab = 0,
        )
        assertBack(SalesPage.REMINDERS, SalesPage.HOME)
        assertBack(SalesPage.REMINDER_DETAIL, SalesPage.REMINDERS)
    }

    @Test
    fun customerPagesUseHomeOrTheRecordedDetailParent() {
        assertBack(SalesPage.CUSTOMERS, SalesPage.HOME)
        assertBack(
            from = SalesNavigationSnapshot(
                currentPage = SalesPage.CUSTOMER_DETAIL,
                detailReturnPage = SalesPage.CUSTOMERS,
            ),
            expectedPage = SalesPage.CUSTOMERS,
        )
        assertBack(
            from = SalesNavigationSnapshot(
                currentPage = SalesPage.CUSTOMER_DETAIL,
                detailReturnPage = SalesPage.CUSTOMER_DETAIL,
            ),
            expectedPage = SalesPage.HOME,
        )
    }

    @Test
    fun registrationAndSubmissionRulesPreserveRequiredCleanup() {
        assertBack(
            from = SalesNavigationSnapshot(currentPage = SalesPage.REGISTRATION),
            expectedPage = SalesPage.HOME,
            expectedEffect = SalesBackEffect.ClearRegistration,
        )
        assertBack(SalesPage.REGISTRATION_CONFIRM, SalesPage.REGISTRATION)
        assertBack(
            from = SalesNavigationSnapshot(currentPage = SalesPage.SUBMIT_SUCCESS),
            expectedPage = SalesPage.HOME,
            expectedEffect = SalesBackEffect.ClearRegistration,
        )
    }

    @Test
    fun evaluationChainReturnsThroughEachDeclaredParent() {
        val choiceParent = SalesPage.SUBMIT_SUCCESS
        assertBack(
            from = SalesNavigationSnapshot(
                currentPage = SalesPage.EVALUATION_CHOICE,
                evaluationChoiceReturnPage = choiceParent,
            ),
            expectedPage = choiceParent,
        )
        assertBack(SalesPage.DEVICE_STATUS, SalesPage.EVALUATION_CHOICE)
        assertBack(SalesPage.EVALUATION_GUIDE, SalesPage.DEVICE_STATUS)
        assertBack(SalesPage.EVALUATION_COMPLETE, SalesPage.HOME)
    }

    private fun assertBack(
        from: SalesPage,
        expectedPage: SalesPage,
    ) {
        assertBack(
            from = SalesNavigationSnapshot(currentPage = from),
            expectedPage = expectedPage,
        )
    }

    private fun assertBack(
        from: SalesNavigationSnapshot,
        expectedPage: SalesPage,
        expectedRootTab: Int = from.rootTab,
        expectedEffect: SalesBackEffect = SalesBackEffect.None,
    ) {
        val result = reduceSalesBack(from)

        assertEquals(expectedPage, result.snapshot.currentPage)
        assertEquals(expectedRootTab, result.snapshot.rootTab)
        assertEquals(expectedEffect, result.effect)
    }
}
