package com.ytone.longcare.common.utils

import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

internal object TimeDisplayDateDelegate {

    @OptIn(ExperimentalTime::class)
    fun getWeeklyDateList(pastDays: Int, futureDays: Int): List<DisplayDate> {
        val dateList = mutableListOf<DisplayDate>()
        val systemTimeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(systemTimeZone)

        val startDate = today.plus(DatePeriod(days = -pastDays))
        val totalDays = pastDays + futureDays + 1

        for (i in 0 until totalDays) {
            val currentDate = startDate.plus(DatePeriod(days = i))
            dateList.add(createDisplayDateFrom(currentDate, today))
        }
        return dateList
    }

    fun getCurrentMonthDateList(
        year: Int?,
        monthNumber: Int?,
        today: LocalDate
    ): List<DisplayDate> {
        val targetYear = year ?: today.year
        val targetMonthNumber = monthNumber ?: today.month.number
        val firstDayOfMonth = LocalDate(targetYear, targetMonthNumber, 1)
        val firstDayOfNextMonth = firstDayOfMonth.plus(1, DateTimeUnit.MONTH)
        val lastDayOfMonth = firstDayOfNextMonth.minus(1, DateTimeUnit.DAY)
        val daysInMonth = lastDayOfMonth.day

        return (1..daysInMonth).map { day ->
            val currentDate = LocalDate(targetYear, targetMonthNumber, day)
            createDisplayDateFrom(currentDate, today)
        }
    }

    fun formatDateForApi(displayDate: DisplayDate): String = displayDate.date.toString()

    private fun createDisplayDateFrom(
        currentDate: LocalDate,
        today: LocalDate
    ): DisplayDate {
        val dayLabel = resolveDayLabel(currentDate, today)
        val monthStr = currentDate.month.number.toString().padStart(2, '0')
        val dayStr = currentDate.day.toString().padStart(2, '0')
        val dateLabelString = "$monthStr/$dayStr"
        return DisplayDate(
            date = currentDate,
            dayOfWeek = dayLabel,
            dateLabel = dateLabelString,
            isToday = currentDate == today
        )
    }

    private fun resolveDayLabel(date: LocalDate, today: LocalDate): DisplayDayLabel {
        val yesterday = today.plus(DatePeriod(days = -1))
        val tomorrow = today.plus(DatePeriod(days = 1))

        return when (date) {
            today -> DisplayDayLabel.TODAY
            yesterday -> DisplayDayLabel.YESTERDAY
            tomorrow -> DisplayDayLabel.TOMORROW
            else -> when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> DisplayDayLabel.MONDAY
                DayOfWeek.TUESDAY -> DisplayDayLabel.TUESDAY
                DayOfWeek.WEDNESDAY -> DisplayDayLabel.WEDNESDAY
                DayOfWeek.THURSDAY -> DisplayDayLabel.THURSDAY
                DayOfWeek.FRIDAY -> DisplayDayLabel.FRIDAY
                DayOfWeek.SATURDAY -> DisplayDayLabel.SATURDAY
                DayOfWeek.SUNDAY -> DisplayDayLabel.SUNDAY
            }
        }
    }
}
