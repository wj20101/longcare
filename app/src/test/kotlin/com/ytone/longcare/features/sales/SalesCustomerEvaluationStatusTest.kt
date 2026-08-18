package com.ytone.longcare.features.sales

import com.ytone.longcare.model.UserLatentDetailModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCustomerEvaluationStatusTest {

    @Test
    fun `form url alone is still unevaluated`() {
        val customer =
            UserLatentDetailModel(
                id = 4,
                pgId = 0,
                pgResult = "",
                pgUrl = "https://careweb.ytone.cn/pinggu?token=test",
            )

        assertFalse(customer.hasCompletedEvaluation())
        assertEquals("", customer.serverAssessmentReportUrl())
    }

    @Test
    fun `evaluation record marks customer as evaluated`() {
        val customer = UserLatentDetailModel(pgId = 18)

        assertTrue(customer.hasCompletedEvaluation())
    }

    @Test
    fun `evaluation result marks customer as evaluated`() {
        val customer =
            UserLatentDetailModel(
                pgResult = "评估完成",
                pgUrl = "  https://careweb.ytone.cn/report/18  ",
            )

        assertTrue(customer.hasCompletedEvaluation())
        assertEquals(
            "https://careweb.ytone.cn/report/18",
            customer.serverAssessmentReportUrl(),
        )
    }
}
