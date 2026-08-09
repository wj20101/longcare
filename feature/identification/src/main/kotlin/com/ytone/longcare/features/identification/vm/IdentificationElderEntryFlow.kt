package com.ytone.longcare.features.identification.vm

import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchElderVerification(
    scope: CoroutineScope,
    orderId: Long,
    orderKey: OrderKey,
    orderDetailRepository: OrderDetailRepository,
    startVerification: (String, String, String, String, VerificationType) -> Unit,
) {
    scope.launch {
        val payload = resolveElderVerificationPayload(
            orderKey = orderKey,
            orderDetailRepository = orderDetailRepository,
        ) ?: return@launch

        startVerification(
            payload.name,
            payload.idNo,
            createElderOrderNo(orderId = orderId),
            payload.userId,
            VerificationType.ELDER
        )
    }
}
