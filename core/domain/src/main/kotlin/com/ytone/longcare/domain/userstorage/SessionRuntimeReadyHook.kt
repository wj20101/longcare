package com.ytone.longcare.domain.userstorage

/** Starts work that is allowed only after a user namespace has reached [UserStorageState.Ready]. */
fun interface SessionRuntimeReadyHook {
    suspend fun onReady(
        identity: SessionRuntimeIdentity,
        lease: UserStorageLease,
    )
}
