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
    fun deviceCopyUsesSimpleName() {
        val resources = ApplicationProvider.getApplicationContext<Application>().resources
        val expected = mapOf(
            R.string.sales_evaluation_device_image_description to "设备",
            R.string.sales_evaluation_ready_to_scan to "准备就绪，请搜索附近的设备",
            R.string.sales_evaluation_scan_devices to "搜索设备",
            R.string.sales_evaluation_scanning to "正在搜索附近的设备…",
            R.string.sales_evaluation_unnamed_device to "未命名设备",
            R.string.sales_evaluation_connecting to "正在连接设备…",
            R.string.sales_error_evaluation_permission to
                "搜索设备需要精确定位权限；Android 12 及以上还需要附近设备权限，请允许后重试",
            R.string.sales_error_evaluation_device_prepare_short to "设备准备失败",
            R.string.sales_error_evaluation_ble_unsupported to "当前手机不支持低功耗蓝牙，无法连接设备",
        )
        expected.forEach { (id, text) -> assertEquals(text, resources.getString(id)) }
    }

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
