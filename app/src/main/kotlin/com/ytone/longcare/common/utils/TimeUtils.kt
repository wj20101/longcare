package com.ytone.longcare.common.utils

import androidx.annotation.StringRes
import com.ytone.longcare.R
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class DisplayDate(
    val timestamp: Long,
    val dayOfWeek: DisplayDayLabel,
    val dateLabel: String,
    val isToday: Boolean
)

enum class DisplayDayLabel(@param:StringRes val labelRes: Int) {
    TODAY(R.string.date_today),
    YESTERDAY(R.string.date_yesterday),
    TOMORROW(R.string.date_tomorrow),
    MONDAY(R.string.date_monday),
    TUESDAY(R.string.date_tuesday),
    WEDNESDAY(R.string.date_wednesday),
    THURSDAY(R.string.date_thursday),
    FRIDAY(R.string.date_friday),
    SATURDAY(R.string.date_saturday),
    SUNDAY(R.string.date_sunday),
}

object TimeUtils {

    @OptIn(ExperimentalTime::class)
    fun getWeeklyDateList(pastDays: Int = 3, futureDays: Int = 3): List<DisplayDate> =
        TimeDisplayDateDelegate.getWeeklyDateList(pastDays = pastDays, futureDays = futureDays)

    @OptIn(ExperimentalTime::class)
    fun getCurrentMonthDateList(year: Int? = null, monthNumber: Int? = null): List<DisplayDate> =
        TimeDisplayDateDelegate.getCurrentMonthDateList(year = year, monthNumber = monthNumber)

    @OptIn(ExperimentalTime::class)
    fun getCurrentInstant(): Instant = TimeCoreDateDelegate.getCurrentInstant()

    @OptIn(ExperimentalTime::class)
    fun getCurrentEpochMilliseconds(): Long = TimeCoreDateDelegate.getCurrentEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        TimeCoreDateDelegate.getCurrentLocalDateTime(timeZone)

    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        TimeCoreDateDelegate.getCurrentLocalDate(timeZone)

    @OptIn(ExperimentalTime::class)
    fun epochMillisToInstant(epochMillis: Long): Instant =
        TimeCoreDateDelegate.epochMillisToInstant(epochMillis)

    @OptIn(ExperimentalTime::class)
    fun epochMillisToLocalDateTime(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): LocalDateTime = TimeCoreDateDelegate.epochMillisToLocalDateTime(epochMillis, timeZone)

    fun epochMillisToLocalDate(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): LocalDate = TimeCoreDateDelegate.epochMillisToLocalDate(epochMillis, timeZone)

    @OptIn(ExperimentalTime::class)
    fun localDateTimeToEpochMillis(localDateTime: LocalDateTime, timeZone: TimeZone): Long =
        TimeCoreDateDelegate.localDateTimeToEpochMillis(localDateTime, timeZone)

    @OptIn(ExperimentalTime::class)
    fun localDateToEpochMillis(
        localDate: LocalDate,
        timeZone: TimeZone,
        atTime: LocalTime = LocalTime(0, 0, 0)
    ): Long = TimeCoreDateDelegate.localDateToEpochMillis(localDate, timeZone, atTime)

    @OptIn(ExperimentalTime::class)
    fun instantPlusDays(instant: Instant, daysToAdd: Int): Instant =
        TimeCoreDateDelegate.instantPlusDays(instant, daysToAdd)

    @OptIn(ExperimentalTime::class)
    fun instantPlusYears(
        instant: Instant,
        yearsToAdd: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant = TimeCoreDateDelegate.instantPlusYears(instant, yearsToAdd, timeZone)

    @OptIn(ExperimentalTime::class)
    fun isToday(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        TimeCoreDateDelegate.isToday(instant, timeZone)

    fun isToday(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = TimeCoreDateDelegate.isToday(localDateTime, referenceTimeZone)

    fun isToday(localDate: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        TimeCoreDateDelegate.isToday(localDate, timeZone)

    @OptIn(ExperimentalTime::class)
    fun isThisMonth(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        TimeCoreDateDelegate.isThisMonth(instant, timeZone)

    fun isThisMonth(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = TimeCoreDateDelegate.isThisMonth(localDateTime, referenceTimeZone)

    @OptIn(ExperimentalTime::class)
    fun isThisYear(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean =
        TimeCoreDateDelegate.isThisYear(instant, timeZone)

    fun isThisYear(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = TimeCoreDateDelegate.isThisYear(localDateTime, referenceTimeZone)

    @OptIn(ExperimentalTime::class)
    fun isSameDay(
        instant1: Instant,
        instant2: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean = TimeCoreDateDelegate.isSameDay(instant1, instant2, timeZone)

    @OptIn(ExperimentalTime::class)
    fun formatDateForApi(displayDate: DisplayDate): String =
        TimeDisplayDateDelegate.formatDateForApi(displayDate)
}
