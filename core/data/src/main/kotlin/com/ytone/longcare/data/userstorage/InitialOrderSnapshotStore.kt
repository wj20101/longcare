package com.ytone.longcare.data.userstorage

import com.ytone.longcare.data.database.entity.InitialOrderSnapshotEntityDb
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.model.ServiceOrderModel
import com.ytone.longcare.model.TodayServiceOrderModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class InitialOrderSnapshotStore @Inject constructor(
    private val databaseAccess: UserDatabaseAccess,
) {
    suspend fun clear(lease: UserStorageLease) {
        databaseAccess.withLease(lease) { database, _ ->
            database.initialOrderSnapshotDao().deleteAll()
        }
    }

    suspend fun replace(
        lease: UserStorageLease,
        todayOrders: List<TodayServiceOrderModel>,
        inProgressOrders: List<ServiceOrderModel>,
    ) {
        val refreshedAt = System.currentTimeMillis()
        val snapshots = todayOrders.map { order -> order.toSnapshot(refreshedAt) } +
            inProgressOrders.map { order -> order.toSnapshot(refreshedAt) }
        databaseAccess.withLease(lease) { database, _ ->
            database.initialOrderSnapshotDao().replaceAll(snapshots)
        }
    }

    internal suspend fun getAll(lease: UserStorageLease): List<InitialOrderSnapshotEntityDb> =
        databaseAccess.withLease(lease) { database, _ ->
            database.initialOrderSnapshotDao().getAll()
        }
}

private fun TodayServiceOrderModel.toSnapshot(refreshedAt: Long) = InitialOrderSnapshotEntityDb(
    listType = InitialOrderListType.TODAY,
    orderId = orderId,
    planId = 0,
    state = state,
    elderUserId = userId,
    elderName = name,
    liveAddress = liveAddress,
    startTime = "",
    endTime = "",
    refreshedAtMillis = refreshedAt,
)

private fun ServiceOrderModel.toSnapshot(refreshedAt: Long) = InitialOrderSnapshotEntityDb(
    listType = InitialOrderListType.IN_PROGRESS,
    orderId = orderId,
    planId = planId,
    state = state,
    elderUserId = userId,
    elderName = name,
    liveAddress = liveAddress,
    startTime = startTime,
    endTime = endTime,
    refreshedAtMillis = refreshedAt,
)

internal object InitialOrderListType {
    const val TODAY = 1
    const val IN_PROGRESS = 2
}
