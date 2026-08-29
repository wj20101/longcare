package com.ytone.longcare.data.userstorage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ytone.longcare.data.database.LongCareDatabase
import com.ytone.longcare.data.startup.AssumedCompletedCutoverGate
import com.ytone.longcare.domain.startup.UserStorageNamespaceCutoverGate
import com.ytone.longcare.domain.userstorage.LeaseValidation
import com.ytone.longcare.domain.userstorage.SessionEpoch
import com.ytone.longcare.domain.userstorage.StorageGeneration
import com.ytone.longcare.domain.userstorage.UserStorageFailure
import com.ytone.longcare.domain.userstorage.UserStorageLease
import com.ytone.longcare.domain.userstorage.UserStorageLeaseAccess
import com.ytone.longcare.domain.userstorage.UserStorageState
import com.ytone.longcare.domain.userstorage.SessionRuntimeIdentity
import com.ytone.longcare.domain.userstorage.validateAgainst
import com.ytone.longcare.model.UserScopeKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UserStorageUnavailableException(message: String) : IllegalStateException(message)

@Singleton
class UserStorageRegistry @Inject constructor(
    private val pathsFactory: UserNamespacePathsFactory,
    private val metadataStore: UserNamespaceMetadataStore,
    private val dataStoreRegistry: UserDataStoreRegistry,
    private val databaseFactory: UserDatabaseFactory,
    private val cutoverGate: UserStorageNamespaceCutoverGate = AssumedCompletedCutoverGate,
) : UserStorageLeaseAccess {
    private data class ActiveStorage(
        val lease: UserStorageLease,
        val database: LongCareDatabase,
        val dataStore: DataStore<Preferences>,
    )

    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<UserStorageState>(UserStorageState.LoggedOut)
    private var generation = 0L
    private var active: ActiveStorage? = null

    val state: StateFlow<UserStorageState> = mutableState.asStateFlow()

    suspend fun open(scopeKey: UserScopeKey, sessionEpoch: SessionEpoch): UserStorageLease {
        cutoverGate.ensureCompleted()
        return mutex.withLock {
        active?.takeIf { it.lease.scopeKey == scopeKey && it.lease.sessionEpoch == sessionEpoch }?.let {
            return@withLock it.lease
        }
        closeActiveLocked()

        val lease = UserStorageLease(
            scopeKey = scopeKey,
            sessionEpoch = sessionEpoch,
            generation = StorageGeneration(++generation),
        )
        mutableState.value = UserStorageState.Opening(lease)
        var openedDatabase: LongCareDatabase? = null
        try {
            val paths = pathsFactory.forScope(scopeKey)
            metadataStore.verifyOrCreate(paths)
            val dataStore = dataStoreRegistry.getOrCreate(paths.dataStoreFile)
            dataStoreRegistry.verifyOrInitializeOwnership(dataStore, scopeKey)
            openedDatabase = databaseFactory.open(scopeKey)
            active = ActiveStorage(lease, openedDatabase, dataStore)
            mutableState.value = UserStorageState.Ready(lease)
            lease
        } catch (error: Exception) {
            openedDatabase?.close()
            active = null
            mutableState.value = UserStorageState.Failed(lease, UserStorageFailure.OPEN_FAILED)
            throw error
        }
        }
    }

    suspend fun close() = mutex.withLock {
        closeActiveLocked()
    }

    /** Revokes all ordinary leases while retaining the database only for trusted cleanup hooks. */
    internal suspend fun revoke() = mutex.withLock {
        val storage = active
        mutableState.value = if (storage == null) {
            UserStorageState.LoggedOut
        } else {
            UserStorageState.Closing(storage.lease)
        }
    }

    override fun currentLeaseOrNull(): UserStorageLease? =
        (mutableState.value as? UserStorageState.Ready)?.lease

    override fun requireCurrentLease(): UserStorageLease =
        currentLeaseOrNull()
            ?: throw UserStorageUnavailableException("User storage is not ready")

    override fun requireValid(lease: UserStorageLease) {
        if (!cutoverGate.isCompleted) {
            throw UserStorageUnavailableException("User-storage cutover is not complete")
        }
        val validation = lease.validateAgainst(mutableState.value)
        if (validation !is LeaseValidation.Valid) {
            throw UserStorageUnavailableException("User storage lease is no longer valid: $validation")
        }
    }

    fun database(lease: UserStorageLease): LongCareDatabase {
        requireValid(lease)
        return active?.takeIf { it.lease == lease }?.database
            ?: throw UserStorageUnavailableException("Database is unavailable")
    }

    fun dataStore(lease: UserStorageLease): DataStore<Preferences> {
        requireValid(lease)
        return active?.takeIf { it.lease == lease }?.dataStore
            ?: throw UserStorageUnavailableException("DataStore is unavailable")
    }

    internal suspend fun <T> withDatabase(
        lease: UserStorageLease,
        block: suspend (LongCareDatabase) -> T,
    ): T = mutex.withLock {
        requireValid(lease)
        val database = active?.takeIf { it.lease == lease }?.database
            ?: throw UserStorageUnavailableException("Database is unavailable")
        block(database)
    }

    internal suspend fun <T> withRevokedDatabase(
        identity: SessionRuntimeIdentity,
        block: suspend (LongCareDatabase) -> T,
    ): T? = mutex.withLock {
        val storage = active ?: return@withLock null
        val state = mutableState.value
        if (
            state !is UserStorageState.Closing ||
            storage.lease.scopeKey != identity.scopeKey ||
            storage.lease.sessionEpoch != identity.sessionEpoch
        ) {
            return@withLock null
        }
        block(storage.database)
    }

    private fun closeActiveLocked() {
        val storage = active
        if (storage != null) {
            mutableState.value = UserStorageState.Closing(storage.lease)
            active = null
            storage.database.close()
        }
        mutableState.value = UserStorageState.LoggedOut
    }
}
