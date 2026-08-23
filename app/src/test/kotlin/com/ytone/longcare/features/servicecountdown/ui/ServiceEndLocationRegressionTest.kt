package com.ytone.longcare.features.servicecountdown.ui

import com.ytone.longcare.common.utils.KLogger
import com.ytone.longcare.features.servicecountdown.api.ServiceCountdownActions
import com.ytone.longcare.features.servicecountdown.vm.ServiceCountdownViewModel
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.model.OrderKey
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServiceEndLocationRegressionTest {

    @Before
    fun setUp() {
        KLogger.updateConfig { enabled = false }
    }

    @Test
    fun `opening end service flow must keep reporting until end order is confirmed`() {
        val orderKey = OrderKey(orderId = 501L, planId = 0)
        val countdownViewModel = mockk<ServiceCountdownViewModel>(relaxed = true)
        var navigatedToConfirmation = false
        val actions = ServiceCountdownActions(
            onNavigateHomeAndClearStack = {},
            onNavigateToEndServiceSelection = { actualOrder, _, _ ->
                navigatedToConfirmation = actualOrder == orderKey
            },
            onNavigateToPhotoUpload = { _, _ -> },
            photoUploadResultFlow =
                MutableStateFlow<Map<ImageTaskType, List<ImageTask>>?>(null),
            clearPhotoUploadResult = {},
        )

        handleEndService(
            orderKey = orderKey,
            projectIdList = listOf(1, 2),
            countdownViewModel = countdownViewModel,
            actions = actions,
            endType = 1,
        )

        assertTrue(navigatedToConfirmation)
    }
}
