package com.ytone.longcare.features.sales

import androidx.navigation3.runtime.NavKey
import com.ytone.longcare.navigation.*
import com.ytone.longcare.presentation.sales.SalesPage
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class SalesEvaluationNavigationTest {
    private val nav = AppNavigator(mutableListOf(AppNavEntry(HomeRoute)), NavigationResults())
    private fun top() = nav.backStack.last() as AppNavEntry
    private fun page() = nav.forEntry(top().id)
    private fun routes() = nav.backStack.map { (it as AppNavEntry).route }

    @Test fun `registration replaces ended pages and camera preserves its caller`() {
        val form = SalesRoute(SalesPage.REGISTRATION)
        page().navigate(form)
        val formEntry = top()
        page().navigate(CameraRoute(com.ytone.longcare.model.WatermarkData("", "", "", "")))
        page().returnResult(com.ytone.longcare.core.navigation.NavigationConstants.CAPTURED_IMAGE_URI_KEY, "content://photo")
        assertEquals(formEntry, top())
        val confirm = SalesRoute(SalesPage.REGISTRATION_CONFIRM,
            draft = SalesCustomerDraft(userName = "测试", liveAddress = "地址", remarks = "备注"),
            photos = listOf("content://photo"), latitude = 31.0, longitude = 121.0)
        page().replaceTop(confirm)
        assertEquals(listOf(HomeRoute, confirm), routes())
        assertEquals(confirm, Json.decodeFromString<SalesRoute>(Json.encodeToString(confirm)))
        assertEquals("测试", (top().route as SalesRoute).draft?.userName)
        val success = SalesRoute(SalesPage.SUBMIT_SUCCESS, 7, "https://form.invalid",
            address = confirm.draft!!.liveAddress, latitude = confirm.latitude, longitude = confirm.longitude)
        val confirmation = page()
        confirmation.replaceTop(success)
        confirmation.replaceTop(success)
        assertEquals(listOf(HomeRoute, success), routes())
        assertNull((top().route as SalesRoute).draft)
        assertTrue((top().route as SalesRoute).photos.isEmpty())
        page().popBackStack()
        assertEquals(listOf(HomeRoute), routes())
    }

    @Test fun `device completion consumes entry while retaining choice and original result context`() {
        val detail = SalesRoute(SalesPage.CUSTOMER_DETAIL, 7)
        val choice = SalesRoute(SalesPage.EVALUATION_CHOICE, 7)
        val device = SalesRoute(SalesPage.DEVICE_STATUS, 7)
        val result = SalesRoute(SalesPage.EVALUATION_COMPLETE, 7, recordId = "record-7")
        page().navigate(detail)
        page().navigate(choice)
        page().navigate(device)
        val sdk = page()
        assertEquals(listOf(HomeRoute, detail, choice, device), routes())
        sdk.replaceTop(result)
        sdk.replaceTop(result)
        assertEquals(listOf(HomeRoute, detail, choice, result), routes())
        page().navigateToEvaluationReport("https://report.invalid", "报告")
        val h5 = page()
        h5.openCustomerDetailsFromH5(8)
        h5.openCustomerDetailsFromH5(9)
        h5.popBackStack()
        assertEquals(listOf(HomeRoute, detail, choice, result, SalesRoute(SalesPage.CUSTOMER_DETAIL, 8)), routes())
        val restored = Json.decodeFromString<List<AppNavEntry>>(Json.encodeToString(nav.backStack.map { it as AppNavEntry }))
        val recreated = AppNavigator(restored.toMutableList<NavKey>(), NavigationResults())
        recreated.forEntry(restored.last().id).popBackStack()
        assertEquals(result, (recreated.backStack.last() as AppNavEntry).route)
        page().popBackStack()
        assertEquals(result, top().route)
        page().popBackStack()
        assertEquals(choice, top().route)
    }

    @Test fun `same customer report closes without duplicate detail and preserves retained entry`() {
        page().navigate(SalesRoute(SalesPage.CUSTOMER_DETAIL, 7))
        val original = top()
        page().navigateToEvaluationReport("https://report.invalid", "报告")
        page().openCustomerDetailsFromH5(7)
        assertEquals(2, nav.backStack.size)
        assertEquals(original.id, top().id)
        assertEquals(original, top())
        page().popBackStack()
        assertEquals(listOf(HomeRoute), routes())
    }

    @Test fun `form detail navigation differs from form close and ordinary web is inactive`() {
        val choice = SalesRoute(SalesPage.EVALUATION_CHOICE, 7)
        page().navigate(choice)
        page().navigateToEvaluationForm("https://form.invalid", "表单")
        assertEquals(7, (top().route as WebViewRoute).evaluationCustomerId)
        page().openCustomerDetailsFromH5(8)
        assertEquals(listOf(HomeRoute, choice, SalesRoute(SalesPage.CUSTOMER_DETAIL, 8)), routes())
        page().popBackStack()
        page().navigateToEvaluationForm("https://form.invalid", "表单")
        page().closeSalesH5(top().route as WebViewRoute)
        assertEquals(SalesRoute(SalesPage.EVALUATION_COMPLETE, 7), top().route)
        page().navigateToWebView("https://ordinary.invalid", "协议")
        val ordinary = top()
        page().openCustomerDetailsFromH5(8)
        assertEquals(ordinary, top())
        page().closeSalesH5(ordinary.route as WebViewRoute)
        assertEquals(SalesRoute(SalesPage.EVALUATION_COMPLETE, 7), top().route)
    }

    @Test fun `canceled device and confirmation pop to actual caller`() {
        val source = SalesRoute(SalesPage.CUSTOMER_DETAIL, 9)
        page().navigate(source)
        page().navigate(SalesRoute(SalesPage.REGISTRATION_CONFIRM, draft = SalesCustomerDraft(userName = "测试")))
        page().popBackStack()
        assertEquals(source, top().route)
        page().navigate(SalesRoute(SalesPage.EVALUATION_CHOICE, 9))
        val choice = top()
        page().navigate(SalesRoute(SalesPage.DEVICE_STATUS, 9))
        page().popBackStack()
        assertEquals(choice, top())
    }
}
