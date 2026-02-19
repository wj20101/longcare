package com.ytone.longcare.common.utils

import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

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
            dateList.add(createDisplayDateFrom(currentDate, today, systemTimeZone))
        }
        return dateList
    }

    @OptIn(ExperimentalTime::class)
    fun getCurrentMonthDateList(year: Int?, monthNumber: Int?): List<DisplayDate> {
        val systemTimeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(systemTimeZone)

        val targetYear = year ?: today.year
        val targetMonthNumber = monthNumber ?: today.month.number
        val firstDayOfMonth = LocalDate(targetYear, targetMonthNumber, 1)
        val firstDayOfNextMonth = firstDayOfMonth.plus(1, DateTimeUnit.MONTH)
        val lastDayOfMonth = firstDayOfNextMonth.minus(1, DateTimeUnit.DAY)
        val daysInMonth = lastDayOfMonth.day

        return (1..daysInMonth).map { day ->
            val currentDate = LocalDate(targetYear, targetMonthNumber, day)
            createDisplayDateFrom(currentDate, today, systemTimeZone)
        }
    }

    @OptIn(ExperimentalTime::class)
    fun formatDateForApi(displayDate: DisplayDate): String {
        val instant = Instant.fromEpochMilliseconds(displayDate.timestamp)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return localDate.toString()
    }

    @OptIn(ExperimentalTime::class)
    private fun createDisplayDateFrom(
        currentDate: LocalDate,
        today: LocalDate,
        timeZone: TimeZone
    ): DisplayDate {
        val dayOfWeekString = formatDayOfWeek(currentDate, today)
        val monthStr = currentDate.month.number.toString().padStart(2, '0')
        val dayStr = currentDate.day.toString().padStart(2, '0')
        val dateLabelString = "$monthStr/$dayStr"
        val timestamp = currentDate.atStartOfDayIn(timeZone).toEpochMilliseconds()

        return DisplayDate(
            timestamp = timestamp,
            dayOfWeek = dayOfWeekString,
            dateLabel = dateLabelString,
            isToday = currentDate == today
        )
    }

    private fun formatDayOfWeek(date: LocalDate, today: LocalDate): String {
        val yesterday = today.plus(DatePeriod(days = -1))
        val tomorrow = today.plus(DatePeriod(days = 1))

        return when (date) {
            today -> "今天"
            yesterday -> "昨天"
            tomorrow -> "明天"
            else -> when (date.dayOfWeek) {
                DayOfWeek.MONDAY -> "周一"
                DayOfWeek.TUESDAY -> "周二"
                DayOfWeek.WEDNESDAY -> "周三"
                DayOfWeek.THURSDAY -> "周四"
                DayOfWeek.FRIDAY -> "周五"
                DayOfWeek.SATURDAY -> "周六"
                DayOfWeek.SUNDAY -> "周日"
            }
        }
    }
}
