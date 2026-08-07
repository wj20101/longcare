package com.ytone.longcare.features.sales

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SalesCustomerWatermarkDataTest {

    @Test
    fun `sales watermark keeps advisor information and omits customer and address`() {
        val watermarkData =
            createSalesCustomerWatermarkData(
                title = "长者顾问",
                advisorName = " 销售顾问 ",
                unknownAdvisorName = "未知顾问",
            )

        assertEquals("长者顾问", watermarkData.title)
        assertEquals("销售顾问", watermarkData.caregiver)
        assertTrue(watermarkData.insuredPerson.isEmpty())
        assertTrue(watermarkData.address.isEmpty())
    }

    @Test
    fun `sales watermark uses formal fallback when advisor name is blank`() {
        val watermarkData =
            createSalesCustomerWatermarkData(
                title = "长者顾问",
                advisorName = " ",
                unknownAdvisorName = "未知顾问",
            )

        assertEquals("未知顾问", watermarkData.caregiver)
    }
}
