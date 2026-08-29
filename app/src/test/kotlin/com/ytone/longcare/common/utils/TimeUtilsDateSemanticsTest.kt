package com.ytone.longcare.common.utils

import com.google.common.truth.Truth.assertThat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import org.junit.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TimeUtilsDateSemanticsTest {
    @Test
    fun `epoch conversion preserves the same instant across time zones`() {
        val epoch = Instant.fromEpochMilliseconds(0)

        assertThat(TimeUtils.epochMillisToLocalDateTime(epoch.toEpochMilliseconds(), TimeZone.UTC))
            .isEqualTo(LocalDateTime(1970, 1, 1, 0, 0))
        assertThat(
            TimeUtils.epochMillisToLocalDateTime(
                epoch.toEpochMilliseconds(),
                TimeZone.of("America/Los_Angeles"),
            ),
        ).isEqualTo(LocalDateTime(1969, 12, 31, 16, 0))
    }

    @Test
    fun `local date start round trips across a daylight saving transition`() {
        val zone = TimeZone.of("America/New_York")
        val date = LocalDate(2024, 3, 10)

        val epochMillis = TimeUtils.localDateToEpochMillis(date, zone, LocalTime(0, 0))

        assertThat(TimeUtils.epochMillisToLocalDate(epochMillis, zone)).isEqualTo(date)
    }

    @Test
    fun `adding calendar years keeps local time semantics in the selected zone`() {
        val zone = TimeZone.of("Asia/Shanghai")
        val source = TimeUtils.localDateTimeToEpochMillis(LocalDateTime(2024, 2, 29, 8, 30), zone)

        val result = TimeUtils.instantPlusYears(Instant.fromEpochMilliseconds(source), 1, zone)

        assertThat(result).isEqualTo(
            Instant.fromEpochMilliseconds(
                TimeUtils.localDateTimeToEpochMillis(LocalDateTime(2025, 2, 28, 8, 30), zone),
            ),
        )
    }
}
