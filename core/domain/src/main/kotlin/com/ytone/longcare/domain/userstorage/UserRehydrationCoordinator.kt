package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.NamespaceId
import kotlinx.coroutines.flow.StateFlow

data class RehydrationIdentity(
    val namespaceId: NamespaceId,
    val sessionEpoch: SessionEpoch,
)

enum class RehydrationDataSource {
    SYSTEM_CONFIG,
    TODAY_ORDERS,
    IN_PROGRESS_ORDERS,
}

sealed interface UserRehydrationState {
    data object Idle : UserRehydrationState
    data class Loading(val identity: RehydrationIdentity) : UserRehydrationState
    data class Ready(
        val identity: RehydrationIdentity,
        val todayOrderCount: Int,
        val inProgressOrderCount: Int,
    ) : UserRehydrationState
    data class Empty(val identity: RehydrationIdentity) : UserRehydrationState
    data class RetryableFailure(
        val identity: RehydrationIdentity,
        val failedSources: Set<RehydrationDataSource>,
    ) : UserRehydrationState
}

interface UserRehydrationCoordinator {
    val state: StateFlow<UserRehydrationState>

    suspend fun retry()
}
