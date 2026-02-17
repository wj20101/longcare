package com.ytone.longcare.features.identification.vm

import android.content.Context
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.model.OrderKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal fun launchElderVerification(
    scope: CoroutineScope,
    context: Context,
    orderId: Long,
    orderKey: OrderKey,
    orderDetailRepository: OrderDetailRepository,
    startVerification: (Context, String, String, String, String, VerificationType) -> Unit,
) {
    scope.launch {
        val payload = resolveElderVerificationPayload(
            orderKey = orderKey,
            orderDetailRepository = orderDetailRepository,
        ) ?: return@launch

        startVerification(
            context,
            payload.name,
            payload.idNo,
            createElderOrderNo(orderId = orderId),
            payload.userId,
            VerificationType.ELDER
        )
    }
}
