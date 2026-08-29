package com.ytone.longcare.domain.userstorage

import com.ytone.longcare.model.UserScopeKey

@JvmInline
value class SessionEpoch(val value: Long) {
    init {
        require(value > 0) { "sessionEpoch must be positive" }
    }
}

@JvmInline
value class StorageGeneration(val value: Long) {
    init {
        require(value > 0) { "generation must be positive" }
    }
}

data class UserStorageLease(
    val scopeKey: UserScopeKey,
    val sessionEpoch: SessionEpoch,
    val generation: StorageGeneration,
)

/**
 * Android- and persistence-free access to the single Ready user-storage lease.
 *
 * Platform coordinators may validate task ownership through this boundary without importing the
 * data-layer registry or obtaining Room/DataStore handles.
 */
interface UserStorageLeaseAccess {
    fun currentLeaseOrNull(): UserStorageLease?

    fun requireCurrentLease(): UserStorageLease

    fun requireValid(lease: UserStorageLease)
}

sealed interface UserStorageState {
    data object LoggedOut : UserStorageState

    data class Opening(val lease: UserStorageLease) : UserStorageState

    data class Ready(val lease: UserStorageLease) : UserStorageState

    data class Closing(val lease: UserStorageLease) : UserStorageState

    data class Failed(
        val attemptedLease: UserStorageLease?,
        val reason: UserStorageFailure,
    ) : UserStorageState
}

enum class UserStorageFailure {
    NAMESPACE_INVALID,
    OPEN_FAILED,
    SESSION_INVALID,
}

enum class LeaseRejection {
    NOT_READY,
    SCOPE_MISMATCH,
    SESSION_EPOCH_MISMATCH,
    GENERATION_EXPIRED,
}

sealed interface LeaseValidation {
    data object Valid : LeaseValidation

    data class Rejected(val reason: LeaseRejection) : LeaseValidation
}

fun UserStorageLease.validateAgainst(state: UserStorageState): LeaseValidation {
    val active = (state as? UserStorageState.Ready)?.lease
        ?: return LeaseValidation.Rejected(LeaseRejection.NOT_READY)
    return when {
        scopeKey != active.scopeKey -> LeaseValidation.Rejected(LeaseRejection.SCOPE_MISMATCH)
        sessionEpoch != active.sessionEpoch -> {
            LeaseValidation.Rejected(LeaseRejection.SESSION_EPOCH_MISMATCH)
        }
        generation != active.generation -> {
            LeaseValidation.Rejected(LeaseRejection.GENERATION_EXPIRED)
        }
        else -> LeaseValidation.Valid
    }
}

object UserStorageTransitionPolicy {
    fun canTransition(from: UserStorageState, to: UserStorageState): Boolean = when (from) {
        UserStorageState.LoggedOut -> to is UserStorageState.Opening
        is UserStorageState.Opening ->
            to is UserStorageState.Ready ||
                to is UserStorageState.Closing ||
                to is UserStorageState.Failed ||
                to === UserStorageState.LoggedOut
        is UserStorageState.Ready -> to is UserStorageState.Closing
        is UserStorageState.Closing ->
            to === UserStorageState.LoggedOut ||
                to is UserStorageState.Opening ||
                to is UserStorageState.Failed
        is UserStorageState.Failed ->
            to === UserStorageState.LoggedOut || to is UserStorageState.Opening
    }
}
