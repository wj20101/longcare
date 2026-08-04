package com.ytone.longcare.features.sales

import com.ytone.longcare.R
import com.ytone.longcare.model.UserLatentCheckState
import org.junit.Assert.assertEquals
import org.junit.Test

class SalesCheckStatePresentationTest {

    @Test
    fun `review label resources match the server contract`() {
        val labelResources =
            mapOf(
                UserLatentCheckState.ALL to R.string.sales_check_state_all,
                UserLatentCheckState.NOT_SUBMITTED to
                    R.string.sales_check_state_not_submitted,
                UserLatentCheckState.PENDING_REVIEW to
                    R.string.sales_check_state_pending_review,
                UserLatentCheckState.APPROVED to
                    R.string.sales_check_state_approved,
                UserLatentCheckState.REJECTED to
                    R.string.sales_check_state_rejected,
            )

        labelResources.forEach { (state, expectedResource) ->
            assertEquals(expectedResource, state.toSalesCheckStateLabelRes())
        }
    }

    @Test
    fun `unknown review state is not presented as all`() {
        assertEquals(
            R.string.sales_check_state_unknown,
            99.toSalesCheckStateLabelRes(),
        )
    }
}
