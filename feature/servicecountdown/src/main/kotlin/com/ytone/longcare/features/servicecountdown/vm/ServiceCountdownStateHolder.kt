package com.ytone.longcare.features.servicecountdown.vm

import com.ytone.longcare.model.ServiceOrderStateModel
import com.ytone.longcare.model.ServiceProjectM
import com.ytone.longcare.model.ImageTask
import com.ytone.longcare.model.ImageTaskType
import com.ytone.longcare.features.servicecountdown.model.ServiceCountdownState
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

class ServiceCountdownStateHolder {
    val countdownState = MutableStateFlow(ServiceCountdownState.RUNNING)
    val remainingTimeMillis = MutableStateFlow(0L)
    val formattedTime = MutableStateFlow("12:00:00")
    val overtimeMillis = MutableStateFlow(0L)
    val uploadedImages = MutableStateFlow<Map<ImageTaskType, List<ImageTask>>>(emptyMap())
    val orderStateError = MutableStateFlow<ServiceOrderStateModel?>(null)

    var countdownJob: Job? = null
    var orderStatePollingJob: Job? = null

    var currentOrderKey: OrderKey? = null
    var currentProjectList: List<ServiceProjectM> = emptyList()
    var currentSelectedProjectIds: List<Int> = emptyList()
}
