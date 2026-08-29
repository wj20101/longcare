package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.WatermarkData
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.CurrentUser

internal suspend fun generateIdentificationWatermarkData(
    address: String,
    orderKey: OrderKey,
    orderDetailRepository: OrderDetailRepository,
    resolveCurrentUser: suspend () -> CurrentUser?,
    unknownElderName: String,
    unknownCaregiverName: String,
    watermarkTitle: String,
): WatermarkData {
    val orderInfo = orderDetailRepository.getCachedOrderInfo(orderKey)
    val elderName = orderInfo?.userInfo?.name ?: unknownElderName
    val caregiverName = resolveCurrentUser()?.userName ?: unknownCaregiverName

    return WatermarkData(
        title = watermarkTitle,
        insuredPerson = elderName,
        caregiver = caregiverName,
        address = address
    )
}
