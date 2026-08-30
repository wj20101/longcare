package com.ytone.longcare.features.sales

import com.ytone.longcare.presentation.sales.SalesNavigationSnapshot
import com.ytone.longcare.presentation.sales.SalesPage
import com.ytone.longcare.presentation.sales.restoreSalesNavigationSnapshot
import com.ytone.longcare.presentation.sales.toSaveableValues
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesNavigationSnapshotTest {
    @Test
    fun snapshotRoundTripPreservesEveryNavigationField() {
        val snapshot = SalesNavigationSnapshot(
            currentPage = SalesPage.EVALUATION_GUIDE,
            rootTab = 2,
            detailReturnPage = SalesPage.CUSTOMERS,
            evaluationChoiceReturnPage = SalesPage.SUBMIT_SUCCESS,
            reminderIndex = 4,
        )

        assertEquals(
            snapshot,
            restoreSalesNavigationSnapshot(snapshot.toSaveableValues()),
        )
    }

    @Test
    fun unknownOrMalformedValuesFallBackWithoutCrashing() {
        assertEquals(
            SalesNavigationSnapshot(),
            restoreSalesNavigationSnapshot(
                listOf("FUTURE_PAGE", 99, "FUTURE_DETAIL", "FUTURE_EVALUATION", -9),
            ),
        )
        assertEquals(
            SalesNavigationSnapshot(),
            restoreSalesNavigationSnapshot(emptyList()),
        )
    }
}
