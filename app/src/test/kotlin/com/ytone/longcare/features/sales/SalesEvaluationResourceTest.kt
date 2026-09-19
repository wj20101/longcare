package com.ytone.longcare.features.sales

import android.app.Application
import android.util.DisplayMetrics
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import com.ytone.longcare.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class SalesEvaluationResourceTest {
    @Test
    fun evaluationIllustrationsUseXxhdpiDensity() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        for (id in listOf(R.drawable.sales_evaluation_instruction, R.drawable.sales_evaluation_hourglass)) {
            val value = TypedValue()
            resources.getValue(id, value, true)
            assertEquals(resources.getResourceEntryName(id), DisplayMetrics.DENSITY_XXHIGH, value.density)
        }
    }
}
