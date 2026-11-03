package com.ytone.longcare.common.utils

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TimeDisplayDateDelegateTest {

    @Test
    fun `month labels use the supplied today snapshot`() {
        val today = LocalDate(2026, 11, 3)
        val dates = TimeUtils.getCurrentMonthDateList(today = today)

        assertEquals(30, dates.size)
        assertEquals(listOf(today), dates.filter { it.isToday }.map { it.date })
        assertEquals(DisplayDayLabel.YESTERDAY, dates[1].dayOfWeek)
        assertEquals(DisplayDayLabel.TODAY, dates[2].dayOfWeek)
        assertEquals(DisplayDayLabel.TOMORROW, dates[3].dayOfWeek)
        assertEquals(DisplayDayLabel.THURSDAY, dates[4].dayOfWeek)
        assertEquals("11/03", dates[2].dateLabel)
    }

    @Test
    fun `month boundary changes the generated month and relative labels`() {
        val october = TimeUtils.getCurrentMonthDateList(today = LocalDate(2026, 10, 31))
        val november = TimeUtils.getCurrentMonthDateList(today = LocalDate(2026, 11, 1))
        val previousMonth = TimeUtils.getCurrentMonthDateList(
            year = 2026,
            monthNumber = 10,
            today = LocalDate(2026, 11, 1)
        )

        assertEquals(31, october.size)
        assertEquals(LocalDate(2026, 10, 31), october.single { it.isToday }.date)
        assertEquals(30, november.size)
        assertEquals(LocalDate(2026, 11, 1), november.first().date)
        assertTrue(november.first().isToday)
        assertEquals(DisplayDayLabel.TOMORROW, november[1].dayOfWeek)
        assertEquals(DisplayDayLabel.YESTERDAY, previousMonth.last().dayOfWeek)
        assertFalse(previousMonth.any { it.isToday })
    }

    @Test
    fun `relative labels and month defaults cross the year boundary`() {
        val december = TimeUtils.getCurrentMonthDateList(today = LocalDate(2026, 12, 31))
        val january = TimeUtils.getCurrentMonthDateList(today = LocalDate(2027, 1, 1))
        val nextMonth = TimeUtils.getCurrentMonthDateList(
            year = 2027,
            monthNumber = 1,
            today = LocalDate(2026, 12, 31)
        )
        val previousMonth = TimeUtils.getCurrentMonthDateList(
            year = 2026,
            monthNumber = 12,
            today = LocalDate(2027, 1, 1)
        )

        assertEquals(LocalDate(2026, 12, 31), december.single { it.isToday }.date)
        assertEquals(LocalDate(2027, 1, 1), january.single { it.isToday }.date)
        assertEquals(DisplayDayLabel.TOMORROW, nextMonth.first().dayOfWeek)
        assertEquals(DisplayDayLabel.YESTERDAY, previousMonth.last().dayOfWeek)
        assertFalse(nextMonth.any { it.isToday })
        assertFalse(previousMonth.any { it.isToday })
    }

    @Test
    fun `February contains the correct last day for leap and common years`() {
        val leapFebruary = TimeUtils.getCurrentMonthDateList(today = LocalDate(2024, 2, 29))
        val commonFebruary = TimeUtils.getCurrentMonthDateList(today = LocalDate(2025, 2, 28))

        assertEquals(29, leapFebruary.size)
        assertEquals(LocalDate(2024, 2, 29), leapFebruary.last().date)
        assertTrue(leapFebruary.last().isToday)
        assertEquals(28, commonFebruary.size)
        assertEquals(LocalDate(2025, 2, 28), commonFebruary.last().date)
        assertTrue(commonFebruary.last().isToday)
    }

    @Test
    fun `API date stays aligned with displayed date after changing default time zone`() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"))
            val date = TimeUtils.getCurrentMonthDateList(today = LocalDate(2026, 11, 3))[2]
            assertEquals("11/03", date.dateLabel)
            assertEquals("2026-11-03", TimeUtils.formatDateForApi(date))

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            assertEquals("2026-11-03", TimeUtils.formatDateForApi(date))

            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals("2026-11-03", TimeUtils.formatDateForApi(date))
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }
}
