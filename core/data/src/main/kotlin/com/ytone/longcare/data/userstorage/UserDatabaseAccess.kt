package com.ytone.longcare.data.userstorage

import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach

internal interface UserDatabaseLeaseSource {
    val state: StateFlow<UserStorageState>

    fun requireCurrentLease(): UserStorageLease

    fun requireValid(lease: UserStorageLease)

    suspend fun <T> withDatabase(
        lease: UserStorageLease,
        block: suspend (LongCareDatabase) -> T,
    ): T
}

private class RegistryDatabaseLeaseSource(
    private val registry: UserStorageRegistry,
) : UserDatabaseLeaseSource {
    override val state: StateFlow<UserStorageState> = registry.state

    override fun requireCurrentLease(): UserStorageLease = registry.requireCurrentLease()

    override fun requireValid(lease: UserStorageLease) = registry.requireValid(lease)

    override suspend fun <T> withDatabase(
        lease: UserStorageLease,
        block: suspend (LongCareDatabase) -> T,
    ): T = registry.withDatabase(lease, block)
}

/**
 * The only ordinary repository entry point to a user database.
 *
 * Room and DAO types remain inside :core:data. Callers must either use the current lease for a
 * short operation or observe through [observeCurrent], which cancels the previous database flow
 * whenever the active storage generation changes.
 */
@Singleton
class UserDatabaseAccess internal constructor(
    private val source: UserDatabaseLeaseSource,
) {
    @Inject
    constructor(registry: UserStorageRegistry) : this(RegistryDatabaseLeaseSource(registry))

    internal suspend fun <T> withCurrentLease(
        block: suspend (LongCareDatabase, UserStorageLease) -> T,
    ): T {
        val lease = source.requireCurrentLease()
        return withLease(lease, block)
    }

    internal fun currentLease(): UserStorageLease = source.requireCurrentLease()

    internal fun requireValid(lease: UserStorageLease) = source.requireValid(lease)

    internal suspend fun <T> withLease(
        lease: UserStorageLease,
        block: suspend (LongCareDatabase, UserStorageLease) -> T,
    ): T = source.withDatabase(lease) { database ->
        source.requireValid(lease)
        block(database, lease)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal fun <T> observeCurrent(
        query: (LongCareDatabase, UserStorageLease) -> Flow<T>,
    ): Flow<T> = source.state.flatMapLatest { storageState ->
        val lease = (storageState as? UserStorageState.Ready)?.lease
            ?: return@flatMapLatest emptyFlow()
        flow {
            val databaseFlow = source.withDatabase(lease) { database -> query(database, lease) }
            emitAll(databaseFlow.onEach { source.requireValid(lease) })
        }
    }
}
