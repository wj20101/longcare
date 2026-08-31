package com.ytone.longcare.features.nursing.ui

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.features.home.nursing.time.TimeUtils
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NursingContractTest {

    @Test
    fun `leap month exposes every date in API format`() {
        val dates = TimeUtils.getCurrentMonthDateList(year = 2024, monthNumber = 2)

        assertThat(dates).hasSize(29)
        assertThat(TimeUtils.formatDateForApi(dates.first())).isEqualTo("2024-02-01")
        assertThat(TimeUtils.formatDateForApi(dates.last())).isEqualTo("2024-02-29")
    }

    @Test
    fun `order taps delegate to explicit nursing actions`() {
        val source = File(
            "src/main/kotlin/com/ytone/longcare/features/nursing/ui/NursingScreen.kt"
        ).readText()

        assertTrue(source.contains("handleOrderNavigation("))
        assertTrue(source.contains("actions.onNavigateToNursingExecution"))
        assertTrue(source.contains("actions.onNavigateToService"))
        assertTrue(source.contains("未开单状态，不允许跳转"))
    }
}
