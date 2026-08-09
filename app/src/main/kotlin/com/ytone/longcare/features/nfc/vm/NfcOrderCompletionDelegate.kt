package com.ytone.longcare.features.nfc.vm

import com.ytone.longcare.common.utils.logE
import com.ytone.longcare.domain.repository.OrderDetailRepository
import com.ytone.longcare.domain.repository.OrderImageRepository
import com.ytone.longcare.features.servicecountdown.domain.ServiceCountdownSystemGateway
import com.ytone.longcare.model.OrderKey
import com.ytone.longcare.navigation.EndOderInfo
import com.ytone.longcare.navigation.ServiceCompleteData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class NfcOrderCompletionDelegate(
    private val unifiedOrderRepository: OrderDetailRepository,
    private val imageRepository: OrderImageRepository,
    private val serviceCountdownSystemGateway: ServiceCountdownSystemGateway,
    private val scope: CoroutineScope
) {

    fun buildServiceCompleteDataFromCache(
        orderKey: OrderKey,
        endOderInfo: EndOderInfo?,
        trueServiceTime: Int
    ): ServiceCompleteData {
        val cachedOrderInfo = unifiedOrderRepository.getCachedOrderInfo(orderKey)
        val userInfo = cachedOrderInfo?.userInfo
        val projectList = cachedOrderInfo?.projectList.orEmpty()
        val selectedProjectIds = endOderInfo?.projectIdList.orEmpty()
        val serviceContent = if (selectedProjectIds.isNotEmpty()) {
            projectList
                .filter { selectedProjectIds.contains(it.projectId) }
                .joinToString(", ") { it.projectName }
        } else {
            projectList.joinToString(", ") { it.projectName }
        }

        return ServiceCompleteData(
            clientName = userInfo?.name.orEmpty(),
            clientAge = userInfo?.age ?: 0,
            clientIdNumber = userInfo?.identityCardNumber.orEmpty(),
            clientAddress = userInfo?.address.orEmpty(),
            serviceContent = serviceContent,
            trueServiceTime = trueServiceTime
        )
    }

    fun cleanupResources(orderKey: OrderKey) {
        try {
            serviceCountdownSystemGateway.stopForegroundService()
            serviceCountdownSystemGateway.stopAlarmRingtone()
            serviceCountdownSystemGateway.cancelCountdownAlarmForOrder(orderKey)

            scope.launch {
                unifiedOrderRepository.endLocalService(orderKey)
                imageRepository.deleteImagesByOrderId(orderKey)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logE("清理服务相关资源失败: ${e.message}", tag = "NfcWorkflowViewModel", throwable = e)
        }
    }
}
