package com.ytone.longcare.common.utils

import kotlinx.datetime.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * 一个通用的、用于UI显示日期信息的数据类。
 * 它不依赖任何特定的UI组件。
 *
 * @param timestamp 当天零点的毫秒级时间戳，用于业务逻辑。
 * @param dayOfWeek 供UI显示的星期字符串，如 "今天", "周一"。
 * @param dateLabel 供UI显示的月/日字符串，如 "06/05"。
 * @param isToday 是否是今天。
 */
data class DisplayDate(
    val timestamp: Long,
    val dayOfWeek: String,
    val dateLabel: String,
    val isToday: Boolean
)

object TimeUtils {

    /**
     * 获取一个以今天为中心的、用于UI选择的日期列表。
     * @param pastDays 显示今天之前的天数。
     * @param futureDays 显示今天之后的天数。
     * @return 返回一个包含格式化日期信息的列表。
     */
    @OptIn(ExperimentalTime::class)
    fun getWeeklyDateList(pastDays: Int = 3, futureDays: Int = 3): List<DisplayDate> {
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

    /**
     * 获取指定月份的所有日期列表。
     *
     * @param year 年份，默认为当年。
     * @param monthNumber 月份 (1-12)，默认为当月。
     * @return 返回该月所有日期的 DisplayDate 列表。
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentMonthDateList(year: Int? = null, monthNumber: Int? = null): List<DisplayDate> {
        val systemTimeZone = TimeZone.currentSystemDefault()
        val today = Clock.System.todayIn(systemTimeZone)

        // 如果未提供年份或月份，则使用当前的年份和月份
        val targetYear = year ?: today.year
        val targetMonthNumber = monthNumber ?: today.month.number

        val firstDayOfMonth = LocalDate(targetYear, targetMonthNumber, 1)

        // 方法：获取下个月的第一天，然后减去一天，得到当前月的最后一天
        val firstDayOfNextMonth = firstDayOfMonth.plus(1, DateTimeUnit.MONTH)
        val lastDayOfMonth = firstDayOfNextMonth.minus(1, DateTimeUnit.DAY)
        val daysInMonth = lastDayOfMonth.day

        // 使用 for 循环代替 while 循环，代码意图更清晰
        return (1..daysInMonth).map { day ->
            val currentDate = LocalDate(targetYear, targetMonthNumber, day)
            createDisplayDateFrom(currentDate, today, systemTimeZone)
        }
    }

    /**
     * 从一个 LocalDate 创建 DisplayDate，这是一个内部辅助函数。
     */
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
        val isToday = currentDate == today

        return DisplayDate(
            timestamp = timestamp,
            dayOfWeek = dayOfWeekString,
            dateLabel = dateLabelString,
            isToday = isToday
        )
    }

    /**
     * 根据规则格式化星期几的显示文本。
     */
    private fun formatDayOfWeek(date: LocalDate, today: LocalDate): String {
        val yesterday = today.plus(DatePeriod(days = -1))
        val tomorrow = today.plus(DatePeriod(days = 1))

        return when (date) {
            today -> "今天"
            yesterday -> "昨天"
            tomorrow -> "明天"
            else -> {
                when (date.dayOfWeek) {
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

    /**
     * 获取当前 Instant (通常是 UTC 时间点)
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentInstant(): Instant = Clock.System.now()

    /**
     * 获取当前毫秒级时间戳
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentEpochMilliseconds(): Long = getCurrentInstant().toEpochMilliseconds()

    /**
     * 获取当前系统默认时区的 LocalDateTime
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDateTime(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        getCurrentInstant().toLocalDateTime(timeZone)

    /**
     * 获取当前系统默认时区的 LocalDate
     */
    @OptIn(ExperimentalTime::class)
    fun getCurrentLocalDate(timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        getCurrentInstant().toLocalDateTime(timeZone).date


    @OptIn(ExperimentalTime::class)
    fun epochMillisToInstant(epochMillis: Long): Instant = Instant.fromEpochMilliseconds(epochMillis)

    @OptIn(ExperimentalTime::class)
    fun epochMillisToLocalDateTime(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        epochMillisToInstant(epochMillis).toLocalDateTime(timeZone)

    fun epochMillisToLocalDate(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
        epochMillisToLocalDateTime(epochMillis, timeZone).date

    @OptIn(ExperimentalTime::class)
    fun localDateTimeToEpochMillis(localDateTime: LocalDateTime, timeZone: TimeZone): Long {
        // LocalDateTime 需要一个 TimeZone 才能明确地转换为一个 Instant (时间点)
        return localDateTime.toInstant(timeZone).toEpochMilliseconds()
    }

    @OptIn(ExperimentalTime::class)
    fun localDateToEpochMillis(localDate: LocalDate, timeZone: TimeZone, atTime: LocalTime = LocalTime(0,0,0)): Long {
        // LocalDate 需要时间 和 TimeZone 才能明确地转换为一个 Instant
        return localDate.atTime(atTime).toInstant(timeZone).toEpochMilliseconds()
    }

    /**
     * 获取几天后的 Instant
     * @param instant 起始 Instant
     * @param daysToAdd 天数 (负数为几天前)
     */
    @OptIn(ExperimentalTime::class)
    fun instantPlusDays(instant: Instant, daysToAdd: Int): Instant =
        instant.plus(daysToAdd.days) // Uses kotlin.time.Duration

    /**
     * 获取几年后的 Instant (注意闰年等复杂性，kotlinx-datetime 会处理)
     * @param instant 起始 Instant
     * @param yearsToAdd 年数
     * @param timeZone 用于计算的日历时区，因为“年”的长度依赖于日历系统
     */
    @OptIn(ExperimentalTime::class)
    fun instantPlusYears(instant: Instant, yearsToAdd: Int, timeZone: TimeZone = TimeZone.currentSystemDefault()): Instant =
        instant.plus(yearsToAdd, DateTimeUnit.YEAR, timeZone)


    /**
     * 判断给定的 Instant 是否是今天 (基于指定时区)
     */
    @OptIn(ExperimentalTime::class)
    fun isToday(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        return instant.toLocalDateTime(timeZone).date == today
    }

    /**
     * 判断给定的 LocalDateTime 是否是今天 (假设 LocalDateTime 已经是目标时区的)
     * 如果需要和当前时区的“今天”比较，确保 `localDateTime` 也是当前时区的。
     */
    fun isToday(localDateTime: LocalDateTime, referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.date == todayInReferenceZone
    }

    /**
     * 判断给定的 LocalDate 是否是今天
     */
    fun isToday(localDate: LocalDate, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        return localDate == getCurrentLocalDate(timeZone)
    }


    /**
     * 判断给定的 Instant 是否是当月 (基于指定时区)
     */
    @OptIn(ExperimentalTime::class)
    fun isThisMonth(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        val dateFromInstant = instant.toLocalDateTime(timeZone).date
        return dateFromInstant.year == today.year && dateFromInstant.month.number == today.month.number
    }

    /**
     * 判断给定的 LocalDateTime 是否是当月
     */
    fun isThisMonth(localDateTime: LocalDateTime, referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.year == todayInReferenceZone.year && localDateTime.month.number == todayInReferenceZone.month.number
    }

    /**
     * 判断给定的 Instant 是否是当年 (基于指定时区)
     */
    @OptIn(ExperimentalTime::class)
    fun isThisYear(instant: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val today = getCurrentLocalDate(timeZone)
        return instant.toLocalDateTime(timeZone).date.year == today.year
    }

    /**
     * 判断给定的 LocalDateTime 是否是当年
     */
    fun isThisYear(localDateTime: LocalDateTime, referenceTimeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        val todayInReferenceZone = getCurrentLocalDate(referenceTimeZone)
        return localDateTime.year == todayInReferenceZone.year
    }

    /**
     * 判断两个 Instant 是否是同一天 (基于指定时区)
     */
    @OptIn(ExperimentalTime::class)
    fun isSameDay(instant1: Instant, instant2: Instant, timeZone: TimeZone = TimeZone.currentSystemDefault()): Boolean {
        return instant1.toLocalDateTime(timeZone).date == instant2.toLocalDateTime(timeZone).date
    }
    
    /**
     * 将 DisplayDate 转换为 API 需要的日期格式 (yyyy-MM-dd)
     * @param displayDate 要转换的 DisplayDate 对象
     * @return 格式化后的日期字符串，如 "2024-01-15"
     */
    @OptIn(ExperimentalTime::class)
    fun formatDateForApi(displayDate: DisplayDate): String {
        val instant = Instant.fromEpochMilliseconds(displayDate.timestamp)
        val localDate = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return localDate.toString()
    }

}