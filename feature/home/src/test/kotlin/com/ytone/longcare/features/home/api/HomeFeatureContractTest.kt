package com.ytone.longcare.features.home.api

import com.google.common.truth.Truth.assertThat
import com.ytone.longcare.core.ui.message.UiMessage
import com.ytone.longcare.core.ui.message.UiText
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.WatermarkData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Test

class HomeFeatureContractTest {

    @Test
    fun `role resolver keeps loading sales and care experiences distinct`() {
        assertThat(resolveHomeExperience(null)).isEqualTo(HomeExperience.Loading)
        assertThat(resolveHomeExperience(2)).isEqualTo(HomeExperience.Sales)
        assertThat(resolveHomeExperience(1)).isEqualTo(HomeExperience.Care)
        assertThat(resolveHomeExperience(3)).isEqualTo(HomeExperience.Care)
    }

    @Test
    fun `order source fake forwards state refresh and message acknowledgement`() {
        val source: HomeOrderStateSource = FakeHomeOrderStateSource()
        val fake = source as FakeHomeOrderStateSource
        val message = UiMessage(7L, UiText.Dynamic("retry"))

        fake.mutableMessages.value = listOf(message)
        source.refreshTodayOrders()
        source.refreshInProgressOrders()
        source.consumeMessage(message.id)

        assertThat(source.todayOrders.value).isEmpty()
        assertThat(source.inProgressOrders.value).isEmpty()
        assertThat(source.messages.value).isEmpty()
        assertThat(fake.todayRefreshCount).isEqualTo(1)
        assertThat(fake.inProgressRefreshCount).isEqualTo(1)
        assertThat(fake.consumedMessageIds).containsExactly(7L)
    }

    @Test
    fun `Home actions forward route payloads and acknowledge camera state explicitly`() {
        val events = mutableListOf<Any>()
        val capturedImage = MutableStateFlow<String?>("content://captured")
        val orderKey = OrderKey(orderId = 99L, planId = 7)
        val watermark = WatermarkData("title", "insured", "caregiver", "address")
        val actions = HomeActions(
            onNavigateToCarePlansList = { events += "care-plans" },
            onNavigateToServiceRecordsList = { events += "service-records" },
            onNavigateToNursingExecution = { events += it },
            onNavigateToService = { events += "service:$it" },
            onNavigateToServiceCountdown = { key, ids -> events += key to ids },
            onNavigateToHaveServiceUserList = { events += "served-users" },
            onNavigateToNoServiceUserList = { events += "unserved-users" },
            onOpenWebPage = { url, title -> events += url to title },
            onOpenUserAgreement = { events += "agreement" },
            onOpenPrivacyPolicy = { events += "privacy" },
            onNavigateToCamera = { events += it },
            capturedImageUriFlow = capturedImage,
            clearCapturedImageUri = { capturedImage.value = null },
        )

        actions.onNavigateToCarePlansList()
        actions.onNavigateToServiceRecordsList()
        actions.onNavigateToNursingExecution(orderKey)
        actions.onNavigateToService(orderKey)
        actions.onNavigateToServiceCountdown(orderKey, listOf(3, 4))
        actions.onNavigateToHaveServiceUserList()
        actions.onNavigateToNoServiceUserList()
        actions.onOpenWebPage("https://open.example/path", "Open")
        actions.onOpenUserAgreement()
        actions.onOpenPrivacyPolicy()
        actions.onNavigateToCamera(watermark)
        actions.clearCapturedImageUri()

        assertThat(events).containsExactly(
            "care-plans",
            "service-records",
            orderKey,
            "service:$orderKey",
            orderKey to listOf(3, 4),
            "served-users",
            "unserved-users",
            "https://open.example/path" to "Open",
            "agreement",
            "privacy",
            watermark,
        ).inOrder()
        assertThat(actions.capturedImageUriFlow.value).isNull()
    }
}

private class FakeHomeOrderStateSource : HomeOrderStateSource {
    override val todayOrders = MutableStateFlow<List<TodayServiceOrderModel>>(emptyList())
    override val inProgressOrders = MutableStateFlow<List<ServiceOrderModel>>(emptyList())
    val mutableMessages = MutableStateFlow<List<UiMessage>>(emptyList())
    override val messages: StateFlow<List<UiMessage>> = mutableMessages
    var todayRefreshCount = 0
    var inProgressRefreshCount = 0
    val consumedMessageIds = mutableListOf<Long>()

    override fun refreshTodayOrders() {
        todayRefreshCount += 1
    }

    override fun refreshInProgressOrders() {
        inProgressRefreshCount += 1
    }

    override fun consumeMessage(id: Long) {
        consumedMessageIds += id
        mutableMessages.value = mutableMessages.value.filterNot { it.id == id }
    }
}
