package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.features.photoupload.model.WatermarkData
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.model.User

internal suspend fun generateIdentificationWatermarkData(
    address: String,
    orderKey: OrderKey,
    orderDetailRepository: OrderDetailRepository,
    resolveCurrentUser: suspend () -> User?,
): WatermarkData {
    val orderInfo = orderDetailRepository.getCachedOrderInfo(orderKey)
    val elderName = orderInfo?.userInfo?.name ?: "未知老人"
    val caregiverName = resolveCurrentUser()?.userName ?: "未知护工"

    return WatermarkData(
        title = "老人照片",
        insuredPerson = elderName,
        caregiver = caregiverName,
        address = address
    )
}
