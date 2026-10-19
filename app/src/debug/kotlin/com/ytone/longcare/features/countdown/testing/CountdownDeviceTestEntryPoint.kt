package com.ytone.longcare.features.countdown.testing

import com.ytone.longcare.domain.location.LocationRecoveryStore
import com.ytone.longcare.domain.repository.UserSessionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Debug-only access to existing dependencies for opt-in, offline alarm device tests. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CountdownDeviceTestEntryPoint {
    fun userSessionRepository(): UserSessionRepository
    fun locationRecoveryStore(): LocationRecoveryStore
}
