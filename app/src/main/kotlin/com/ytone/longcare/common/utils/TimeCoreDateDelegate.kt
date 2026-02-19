package com.ytone.longcare.common.utils

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

internal object TimeCoreDateDelegate {

    @OptIn(ExperimentalTime::class)
    fun getCurrentInstant(): Instant = Clock.System.now()

    @OptIn(ExperimentalTime::class)
    fun getCurrentEpochMilliseconds(): Long = getCurrentInstant().toEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        getCurrentInstant().toLocalDateTime(timeZone)

    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        getCurrentLocalDateTime(timeZone).date

    @OptIn(ExperimentalTime::class)
    fun epochMillisToInstant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

    @OptIn(ExperimentalTime::class)
    fun epochMillisToLocalDateTime(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): LocalDateTime = epochMillisToInstant(epochMillis).toLocalDateTime(timeZone)

    fun epochMillisToLocalDate(
        epochMillis: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): LocalDate = epochMillisToLocalDateTime(epochMillis, timeZone).date

    @OptIn(ExperimentalTime::class)
    fun localDateTimeToEpochMillis(localDateTime: LocalDateTime, timeZone: TimeZone): Long =
        localDateTime.toInstant(timeZone).toEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    fun localDateToEpochMillis(
        localDate: LocalDate,
        timeZone: TimeZone,
        atTime: LocalTime = LocalTime(0, 0, 0)
    ): Long = localDate.atTime(atTime).toInstant(timeZone).toEpochMilliseconds()

    @OptIn(ExperimentalTime::class)
    fun instantPlusDays(instant: Instant, daysToAdd: Int): Instant =
        instant.plus(daysToAdd.days)

    @OptIn(ExperimentalTime::class)
    fun instantPlusYears(
        instant: Instant,
        yearsToAdd: Int,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Instant = instant.plus(yearsToAdd, DateTimeUnit.YEAR, timeZone)

    @OptIn(ExperimentalTime::class)
    fun isToday(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        return instant.toLocalDateTime(timeZone).date == today
    }

    fun isToday(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.date == todayInReferenceZone
    }

    fun isToday(localDate: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        return localDate == getCurrentLocalDate(timeZone)
    }

    @OptIn(ExperimentalTime::class)
    fun isThisMonth(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        val dateFromInstant = instant.toLocalDateTime(timeZone).date
        return dateFromInstant.year == today.year && dateFromInstant.month == today.month
    }

    fun isThisMonth(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.year == todayInReferenceZone.year && localDateTime.month == todayInReferenceZone.month
    }

    @OptIn(ExperimentalTime::class)
    fun isThisYear(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        return instant.toLocalDateTime(timeZone).date.year == today.year
    }

    fun isThisYear(
        localDateTime: LocalDateTime,
        referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.year == todayInReferenceZone.year
    }

    @OptIn(ExperimentalTime::class)
    fun isSameDay(
        instant1: Instant,
        instant2: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault()
    ): Boolean {
        return instant1.toLocalDateTime(timeZone).date == instant2.toLocalDateTime(timeZone).date
    }
}
