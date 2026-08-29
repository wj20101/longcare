package com.ytone.longcare.data.userstorage

import com.ytone.longcare.api.LongCareApiService
import com.ytone.longcare.common.utils.SystemConfigManager
import com.ytone.longcare.data.session.SessionOperationTracker
import com.ytone.longcare.domain.userstorage.RehydrationDataSource
import com.ytone.longcare.domain.userstorage.RehydrationIdentity
import com.ytone.longcare.domain.userstorage.SessionRuntimeCleanupHook
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.SessionRuntimeReadyHook
import com.ytone.longcare.domain.userstorage.UserRehydrationCoordinator
import com.ytone.longcare.domain.userstorage.UserRehydrationState
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import com.ytone.longcare.model.result.ApiResult
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class DefaultUserRehydrationCoordinator @Inject internal constructor(
    private val apiService: LongCareApiService,
    private val systemConfigManager: SystemConfigManager,
    private val databaseAccess: UserDatabaseAccess,
    private val orderSnapshotStore: InitialOrderSnapshotStore,
) : UserRehydrationCoordinator, SessionRuntimeReadyHook, SessionRuntimeCleanupHook {
    private data class FetchResult<T>(
        val succeeded: Boolean,
        val value: T,
    )

    private val activeLease = AtomicReference<UserStorageLease?>(null)
    private val operationTracker = SessionOperationTracker()
    private val rehydrationMutex = Mutex()
    private val mutableState = MutableStateFlow<UserRehydrationState>(UserRehydrationState.Idle)

    override val state: StateFlow<UserRehydrationState> = mutableState.asStateFlow()

    override suspend fun onReady(
        identity: SessionRuntimeIdentity,
        lease: UserStorageLease,
    ) {
        require(identity.scopeKey == lease.scopeKey && identity.sessionEpoch == lease.sessionEpoch) {
            "Ready hook identity does not match the storage lease"
        }
        activeLease.set(lease)
        rehydrate(identity, lease)
    }

    override suspend fun retry() {
        val lease = activeLease.get() ?: throw UserStorageUnavailableException(
            "Cannot retry rehydration without an active user storage lease"
        )
        rehydrate(
            identity = SessionRuntimeIdentity(lease.scopeKey, lease.sessionEpoch),
            lease = lease,
        )
    }

    override suspend fun cleanup(identity: SessionRuntimeIdentity) {
        val fingerprint = identity.fingerprint()
        operationTracker.cancelAndJoin(fingerprint)
        val lease = activeLease.get()
        if (
            lease != null &&
            lease.scopeKey == identity.scopeKey &&
            lease.sessionEpoch == identity.sessionEpoch &&
            activeLease.compareAndSet(lease, null)
        ) {
            mutableState.value = UserRehydrationState.Idle
        }
    }

    private suspend fun rehydrate(
        identity: SessionRuntimeIdentity,
        lease: UserStorageLease,
    ) {
        operationTracker.track(
            sessionFingerprint = identity.fingerprint(),
            validateSession = { requireActive(lease) },
        ) {
            rehydrationMutex.withLock {
                requireActive(lease)
                publishIfActive(lease, UserRehydrationState.Loading(identity.toRehydrationIdentity()))
                orderSnapshotStore.clear(lease)

                val (configResult, todayResult, inProgressResult) = coroutineScope {
                    val config = async { fetchSystemConfig(lease) }
                    val today = async { fetchTodayOrders(lease) }
                    val inProgress = async { fetchInProgressOrders(lease) }
                    Triple(config.await(), today.await(), inProgress.await())
                }

                requireActive(lease)
                val failures = buildSet {
                    if (!configResult) add(RehydrationDataSource.SYSTEM_CONFIG)
                    if (!todayResult.succeeded) add(RehydrationDataSource.TODAY_ORDERS)
                    if (!inProgressResult.succeeded) add(RehydrationDataSource.IN_PROGRESS_ORDERS)
                }

                if (todayResult.succeeded && inProgressResult.succeeded) {
                    orderSnapshotStore.replace(
                        lease = lease,
                        todayOrders = todayResult.value,
                        inProgressOrders = inProgressResult.value,
                    )
                }
                requireActive(lease)

                val nextState = when {
                    failures.isNotEmpty() -> UserRehydrationState.RetryableFailure(
                        identity = identity.toRehydrationIdentity(),
                        failedSources = failures,
                    )
                    todayResult.value.isEmpty() && inProgressResult.value.isEmpty() ->
                        UserRehydrationState.Empty(identity.toRehydrationIdentity())
                    else -> UserRehydrationState.Ready(
                        identity = identity.toRehydrationIdentity(),
                        todayOrderCount = todayResult.value.size,
                        inProgressOrderCount = inProgressResult.value.size,
                    )
                }
                publishIfActive(lease, nextState)
            }
        }
    }

    private suspend fun fetchSystemConfig(lease: UserStorageLease): Boolean = try {
        systemConfigManager.forceRehydrate(lease)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        requireActive(lease)
        false
    }

    private suspend fun fetchTodayOrders(
        lease: UserStorageLease,
    ): FetchResult<List<TodayServiceOrderModel>> = fetchList(lease) {
        apiService.getTodayOrderList()
    }

    private suspend fun fetchInProgressOrders(
        lease: UserStorageLease,
    ): FetchResult<List<ServiceOrderModel>> = fetchList(lease) {
        apiService.getInOrderList()
    }

    private suspend fun <T> fetchList(
        lease: UserStorageLease,
        request: suspend () -> ApiResult<List<T>>,
    ): FetchResult<List<T>> = try {
        when (val result = request()) {
            is ApiResult.Success -> {
                requireActive(lease)
                FetchResult(succeeded = true, value = result.data)
            }
            is ApiResult.Failure -> {
                requireActive(lease)
                FetchResult(succeeded = false, value = emptyList())
            }
            is ApiResult.Exception -> {
                if (result.exception is CancellationException) throw result.exception
                requireActive(lease)
                FetchResult(succeeded = false, value = emptyList())
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        requireActive(lease)
        FetchResult(succeeded = false, value = emptyList())
    }

    private fun requireActive(lease: UserStorageLease) {
        if (activeLease.get() != lease) {
            throw CancellationException("Rehydration belongs to an expired user session")
        }
        try {
            databaseAccess.requireValid(lease)
        } catch (error: Exception) {
            throw CancellationException("Rehydration storage lease was revoked").also {
                it.initCause(error)
            }
        }
    }

    private fun publishIfActive(lease: UserStorageLease, state: UserRehydrationState) {
        requireActive(lease)
        mutableState.value = state
    }

    private fun SessionRuntimeIdentity.fingerprint(): String =
        "${scopeKey.namespaceId().value}:${sessionEpoch.value}"

    private fun SessionRuntimeIdentity.toRehydrationIdentity() = RehydrationIdentity(
        namespaceId = scopeKey.namespaceId(),
        sessionEpoch = sessionEpoch,
    )
}
