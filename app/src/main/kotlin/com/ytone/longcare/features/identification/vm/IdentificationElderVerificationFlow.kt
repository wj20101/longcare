package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey

internal data class ElderVerificationPayload(
    val name: String,
    val idNo: String,
    val userId: String,
)

internal suspend fun resolveElderVerificationPayload(
    orderKey: OrderKey,
    orderDetailRepository: OrderDetailRepository,
): ElderVerificationPayload? {
    val orderInfo = orderDetailRepository.getCachedOrderInfo(orderKey) ?: return null
    val userInfo = orderInfo.userInfo ?: return null
    return ElderVerificationPayload(
        name = userInfo.name,
        idNo = userInfo.identityCardNumber,
        userId = userInfo.userId.toString(),
    )
}
